package constants;

public enum Exchange {
    COINBASE_PRO("CoinbasePro"),
    KRAKEN("Kraken");

    private String str;
    Exchange(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return this.str;
    }
}
