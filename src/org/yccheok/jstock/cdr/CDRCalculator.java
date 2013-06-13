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
    
    public static CDRRegressionParameters regression(StockInfo stockInfo, CDRSettings settings, Date startFrom) {
        StockHistoryServer stockHistoryServer = MainFrame.getInstance().getStockHistoryServer(stockInfo.code);
        StockHistoryServer[] refHistoryServers = new StockHistoryServer[settings.references.size()];
        for (int i = 0; i < refHistoryServers.length; i++) {
            refHistoryServers[i] = MainFrame.getInstance().getStockHistoryServer(settings.references.get(i).code);
        }
        
        int nobs = settings.period;
        int nvars = settings.references.size();
        double[] data = new double[nobs * (nvars + 1)];
                
        for (int i = 0; i < nobs; i++) {
            final int index = stockHistoryServer.size() - nobs + i;
            final long timestamp1 = stockHistoryServer.getTimestamp(index);                       
            Stock stock1 = stockHistoryServer.getStock(timestamp1);
                        
            final long timestamp2 = stockHistoryServer.getTimestamp(i - 1);
            Stock stock2 = stockHistoryServer.getStock(timestamp2);
                       
            data[nobs * (nvars + 1)] = cdr(stock1.getLastPrice(), stock2.getLastPrice());
            
            for (int j = 0; j < nvars; j++) {
                Stock ref1 = refHistoryServers[j].getStock(timestamp1);
                Stock ref2 = refHistoryServers[j].getStock(timestamp2);
                data[nobs * (nvars + 1) + j + 1] = cdr(ref1.getLastPrice(), ref2.getLastPrice());
            }
        }
        
        AbstractMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(data, nobs, nvars);
        
        CDRRegressionParameters params = new CDRRegressionParameters();
        params.parameters = regression.estimateRegressionParameters();
        params.variance = regression.estimateErrorVariance();
                
        return params;
    }
    
    public static double[] regression(StockHistoryServer stockHistoryServer, CDRSettings settings, long[] timestamps, int period) {        
        StockHistoryServer[] refHistoryServers = new StockHistoryServer[settings.references.size()];
        for (int i = 0; i < refHistoryServers.length; i++) {
            refHistoryServers[i] = MainFrame.getInstance().getStockHistoryServer(settings.references.get(i).code);
        }
        
        int nobs = timestamps.length - period;
        int nvars = settings.references.size();
        double[] data = new double[nobs * (nvars + 1)];
                
        for (int i = period; i < nobs; i++) {            
            final long timestamp1 = timestamps[i];                       
            Stock stock1 = stockHistoryServer.getStock(timestamp1);
                        
            final long timestamp2 = timestamps[i - period];
            Stock stock2 = stockHistoryServer.getStock(timestamp2);
                       
            data[i * (nvars + 1)] = cdr(stock1.getLastPrice(), stock2.getLastPrice());
            
            for (int j = 0; j < nvars; j++) {
                Stock ref1 = refHistoryServers[j].getStock(timestamp1);
                Stock ref2 = refHistoryServers[j].getStock(timestamp2);
                data[i * (nvars + 1) + j + 1] = cdr(ref1.getLastPrice(), ref2.getLastPrice());
            }
        }
        
        AbstractMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(data, nobs, nvars);
        
        return regression.estimateResiduals();
    }
}
