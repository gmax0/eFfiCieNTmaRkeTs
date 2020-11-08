package config;

import org.apache.commons.configuration2.convert.DefaultConversionHandler;
import org.apache.commons.configuration2.interpol.ConfigurationInterpolator;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomConversionHandler extends DefaultConversionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CustomConversionHandler.class);
    @Override
    protected <T> T convertValue(Object src, Class<T> targetCls, ConfigurationInterpolator ci) {
        if (src == null) {
            return null;
        } else if (CurrencyPair.class.equals(targetCls)) {
            try {
                return (T)(CurrencyPair.class.getDeclaredField((String) src)).get(this);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LOG.error("Exception caught: ", e);
                return null;
            }
        } else {
            return super.convertValue(src, targetCls, ci);
        }
    }
}
