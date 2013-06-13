/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.yccheok.jstock.cdr;

import java.util.Date;
import org.apache.commons.math3.stat.regression.AbstractMultipleLinearRegression;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.yccheok.jstock.engine.Stock;
import org.yccheok.jstock.engine.StockHistoryServer;
import org.yccheok.jstock.engine.StockInfo;
import org.yccheok.jstock.gui.MainFrame;

/**
 *
 * @author Jack
 */
public class CDRCalculator {

    public static double cdr(double price1, double price2) {
        return Math.log(price1 / price2) * 100;
    }

    public static double[] regression(StockHistoryServer stockHistoryServer, CDRSettings settings, long[] timestamps, int period) {
        StockHistoryServer[] refHistoryServers = new StockHistoryServer[settings.references.size()];
        for (int i = 0; i < refHistoryServers.length; i++) {
            refHistoryServers[i] = MainFrame.getInstance().getStockHistoryServer(settings.references.get(i).code);            
        }

        int length = timestamps.length - period;
        if (length <= 0) {
            return null;
        }

        int nvars = settings.references.size();
        double[] result = new double[length];

        for (int i = period + 1; i < timestamps.length; i++) {
            double[] data = new double[period * (nvars + 1)];
            final long timestamp2 = timestamps[i - period];
            Stock stock2 = stockHistoryServer.getStock(timestamp2);
                
            for (int k = 0; k < period; k++) {
                final long timestamp1 = timestamps[i + k - period];
                Stock stock1 = stockHistoryServer.getStock(timestamp1);
                
                data[k * (nvars + 1)] = cdr(stock1.getLastPrice(), stock2.getLastPrice());
                for (int j = 0; j < nvars; j++) {
                    Stock ref1 = refHistoryServers[j].getStock(timestamp1);
                    Stock ref2 = refHistoryServers[j].getStock(timestamp2);
                    if (ref1 == null || ref2 == null) {
                        data[k * (nvars + 1) + j + 1] = 0.0;
                    } else {
                        data[k * (nvars + 1) + j + 1] = cdr(ref1.getLastPrice(), ref2.getLastPrice());
                    }
                }
            }

            try {
                AbstractMultipleLinearRegression regression = new OLSMultipleLinearRegression();
                regression.newSampleData(data, period, nvars);
                double[] residuals = regression.estimateResiduals();
                result[i - period] = residuals[period - 1];
            } catch (Exception ex) {
                result[i - period] = 0.0;
            }
        }

        return result;
    }
}
