package util.task;

import domain.ExchangeLimitOrder;
import domain.Trade;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.MetadataAggregator;
import services.arbitrage.SpatialArbitragerV2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static domain.constants.OrderType.LIMIT;
import static org.knowm.xchange.dto.Order.OrderType.ASK;
import static org.knowm.xchange.dto.Order.OrderType.BID;

public class ComputeArbitrageTaskV2 implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ComputeArbitrageTaskV2.class);

    private SpatialArbitragerV2 spatialArbitragerV2;
    private List<ExchangeLimitOrder> aggregatedAsks;
    private List<ExchangeLimitOrder> aggregatedBids;
    private CurrencyPair currencyPair;
    private MetadataAggregator metadataAggregator;

    public ComputeArbitrageTaskV2(SpatialArbitragerV2 spatialArbitragerV2,
                                  List<ExchangeLimitOrder> aggregatedAsks,
                                  List<ExchangeLimitOrder> aggregatedBids,
                                  CurrencyPair currencyPair,
                                  MetadataAggregator metadataAggregator) {
        this.spatialArbitragerV2 = spatialArbitragerV2;
        this.aggregatedAsks = aggregatedAsks;
        this.aggregatedBids = aggregatedBids;
        this.currencyPair = currencyPair;
        this.metadataAggregator = metadataAggregator;
    }

    @Override
    public void run() {
        try {
            LOG.debug("Running computeTrades()");
            computeTrades();
            LOG.debug("Completed computeTrades()");
        } catch (Exception e) {
            Thread.currentThread()
                    .getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), e);
        }
    }

    /**
     * Performs the following steps:
     * 1. Sorts aggregated asks in ascending order, aggregated bids in descending order
     * 2. Begins iterating through the list of asks, setting a bid price floor for the current ask's price.
     * 3. Valid arbitrage opportunities are added to the list of trades, passed back to SpatialArbitragerV2 via callback.
     */
    private void computeTrades() {
        aggregatedAsks.sort(SpatialArbitragerV2.ascendingAskComparator);
        aggregatedBids.sort(SpatialArbitragerV2.descendingBidComparator);

        BigDecimal minGain = spatialArbitragerV2.getMinGain();

        for (int i = 0; i < aggregatedAsks.size(); i++) {
            ExchangeLimitOrder ask = aggregatedAsks.get(i);
            BigDecimal bidPriceFloor = ask.getLimitOrder().getLimitPrice().multiply(BigDecimal.ONE.add(minGain));

            for (int j = 0; j < aggregatedBids.size(); j++) {
                ExchangeLimitOrder bid = aggregatedBids.get(i);
                if (bid.getLimitOrder().getLimitPrice().compareTo(bidPriceFloor) <= 0) {
                    if (j == 0) {
                        return;
                    } else {
                        break;
                    }
                }
                if (ask.getExchange().equals(bid.getExchange())) continue;

                //Get Fees and Minimum Order Volumes
                BigDecimal ex1TakerFee = metadataAggregator.getFees(ask.getExchange(), currencyPair).getTakerFee();
                BigDecimal ex2TakerFee = metadataAggregator.getFees(bid.getExchange(), currencyPair).getTakerFee();

                BigDecimal ex1MinOrderAmount =
                        metadataAggregator.getMinimumOrderAmount(ask.getExchange(), currencyPair);
                BigDecimal ex2MinOrderAmount =
                        metadataAggregator.getMinimumOrderAmount(bid.getExchange(), currencyPair);

                //Ensure Minimum Volume Requirements are met
                BigDecimal effectiveBaseOrderVolume = ask.getLimitOrder().getOriginalAmount().min(
                        bid.getLimitOrder().getOriginalAmount()
                );
                if (effectiveBaseOrderVolume.compareTo(ex1MinOrderAmount) < 0
                        || effectiveBaseOrderVolume.compareTo(ex2MinOrderAmount) < 0) {
                    continue;
                }

                //Perform Arbitrage Calculations
                BigDecimal costToBuy = ask.getLimitOrder().getLimitPrice().multiply(effectiveBaseOrderVolume);
                BigDecimal totalCostToBuy = costToBuy.add(costToBuy.multiply(ex1TakerFee));

                BigDecimal incomeSold = bid.getLimitOrder().getLimitPrice().multiply(effectiveBaseOrderVolume);
                BigDecimal totalIncomeSold = incomeSold.subtract(incomeSold.multiply(ex2TakerFee));

                //Valid Arbitrage Opportunity
                if (totalIncomeSold.compareTo(totalCostToBuy) > 0
                        && totalIncomeSold
                        .subtract(totalCostToBuy)
                        .divide(totalCostToBuy, 5, RoundingMode.HALF_EVEN)
                        .compareTo(minGain)
                        >= 0) {

                    LOG.info(
                            "Arbitrage Opportunity Detected for {} ! Buy {} units on {} at {}, Sell {} units on {} at {}",
                            currencyPair,
                            effectiveBaseOrderVolume,
                            ask.getExchange(),
                            ask.getLimitOrder().getLimitPrice(),
                            effectiveBaseOrderVolume,
                            bid.getExchange(),
                            bid.getLimitOrder().getLimitPrice());
                    LOG.info(
                            "With fees calculated, Cost To Buy: {} , Amount Sold: {}, Profit: {}",
                            totalCostToBuy,
                            totalIncomeSold,
                            totalIncomeSold.subtract(totalCostToBuy));

                    Trade buyLow =
                            Trade.builder()
                                    .exchange(ask.getExchange())
                                    .currencyPair(currencyPair)
                                    .orderActionType(BID)
                                    .orderType(LIMIT)
                                    .price(ask.getLimitOrder().getLimitPrice())
                                    .amount(effectiveBaseOrderVolume)
                                    .timeDiscovered(Instant.now())
                                    .fee(ex1TakerFee)
                                    .build();
                    Trade sellHigh =
                            Trade.builder()
                                    .exchange(bid.getExchange())
                                    .currencyPair(currencyPair)
                                    .orderActionType(ASK)
                                    .orderType(LIMIT)
                                    .price(bid.getLimitOrder().getLimitPrice())
                                    .amount(effectiveBaseOrderVolume)
                                    .timeDiscovered(Instant.now())
                                    .fee(ex2TakerFee)
                                    .build();
                    spatialArbitragerV2.callback(buyLow, sellHigh);

                    //Update Volumes
                    aggregatedAsks.set(i, ExchangeLimitOrder.builder()
                            .exchange(ask.getExchange())
                            .limitOrder(new LimitOrder(
                                    ASK,
                                    ask.getLimitOrder().getOriginalAmount().subtract(effectiveBaseOrderVolume),
                                    null,
                                    null,
                                    null,
                                    ask.getLimitOrder().getLimitPrice())).build());
                    aggregatedBids.set(j, ExchangeLimitOrder.builder()
                            .exchange(bid.getExchange())
                            .limitOrder(new LimitOrder(
                                    BID,
                                    bid.getLimitOrder().getOriginalAmount().subtract(effectiveBaseOrderVolume),
                                    null,
                                    null,
                                    null,
                                    bid.getLimitOrder().getLimitPrice())).build());
                }
            }
        }
    }
}
