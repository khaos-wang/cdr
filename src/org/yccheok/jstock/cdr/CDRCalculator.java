/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.yccheok.jstock.cdr;

import au.com.bytecode.opencsv.CSVWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.Date;
import org.apache.commons.math3.stat.regression.AbstractMultipleLinearRegression;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.yccheok.jstock.engine.SimpleDate;
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
            if (stockHistoryServer == refHistoryServers[i]) {
                return null;
            }
        }

        int length = timestamps.length - 1;
        if (length <= 0) {
            return null;
        }

        int nvars = settings.references.size();
        double[] delta = new double[length];
       
        for (int i = period + 1; i < timestamps.length; i++) {
            double[] data = new double[period * (nvars + 1)];

            for (int k = 0; k < period; k++) {
                final long timestamp1 = timestamps[i + k + 1 - period];
                Stock stock1 = stockHistoryServer.getStock(timestamp1);
                final long timestamp2 = timestamps[i + k - period];
                Stock stock2 = stockHistoryServer.getStock(timestamp2);
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
                OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
                regression.newSampleData(data, period, nvars);

                double[] residuals = regression.estimateResiduals();
                assert (residuals.length == period);
                if (i == period + 1) {
                    for (int j = 0; j < i - 1; j++) {
                        delta[j] = residuals[j];
                    }
                } else {
                    delta[i - 1] = residuals[residuals.length - 1];
                }
            } catch (Exception ex) {
                delta[i - 1] = 0.0;
            }
        }

        double[] cdr = new double[delta.length];
        cdr[0] = delta[0];
        for (int i = 1; i < cdr.length; i++) {
            cdr[i] = cdr[i - 1] + delta[i];
        }
        return cdr;
    }

    public static void saveRegression(File file, StockHistoryServer stockHistoryServer, CDRSettings settings, int period) {
        StockHistoryServer[] refHistoryServers = new StockHistoryServer[settings.references.size()];
        for (int i = 0; i < refHistoryServers.length; i++) {
            refHistoryServers[i] = MainFrame.getInstance().getStockHistoryServer(settings.references.get(i).code);
            if (stockHistoryServer == refHistoryServers[i]) {
                return;
            }
        }

        Calendar date = settings.baseDate.getCalendar();
        long ts = date.getTimeInMillis();
        int start = 1;
        for (; stockHistoryServer.getTimestamp(start) <= ts && start < stockHistoryServer.size(); ++start) {
        }
        
        int length = stockHistoryServer.size() - start;
        if (length <= 0) {
            return;
        }

        FileOutputStream fileOutputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        CSVWriter csvwriter = null;

        int intercept = 1;
        try {
            fileOutputStream = new FileOutputStream(file);
            outputStreamWriter = new OutputStreamWriter(fileOutputStream, Charset.forName("UTF-8"));
            csvwriter = new CSVWriter(outputStreamWriter);
            String[] fields = new String[8 + refHistoryServers.length * 2 + intercept];
            fields[0] = "Date";
            fields[1] = "Close";
            for (int i = 0; i < refHistoryServers.length; i++) {
                fields[2 + i] = refHistoryServers[i].getStock(refHistoryServers[i].getTimestamp(0)).code.toString();
            }
            fields[2 + refHistoryServers.length] = "Real";
            
            if (intercept > 0) {
                fields[3 + refHistoryServers.length] = "Intercept";
            }
            
            for (int i = 0; i < refHistoryServers.length; i++) {
                fields[3 + intercept + refHistoryServers.length + i] = "Beta of " + refHistoryServers[i].getStock(refHistoryServers[i].getTimestamp(0)).code.toString();
            }
            
            fields[3 + intercept + refHistoryServers.length * 2] = "R";
            fields[4 + intercept + refHistoryServers.length * 2] = "Theory";
            fields[5 + intercept + refHistoryServers.length * 2] = "Delta";
            fields[6 + intercept + refHistoryServers.length * 2] = "CDR";
            fields[7 + intercept + refHistoryServers.length * 2] = "CR";
            csvwriter.writeNext(fields);

            int nvars = settings.references.size();
            long[] dates = new long[length];
            double[] delta = new double[length];
            double[] real = new double[length];
            double[] params = new double[length * (refHistoryServers.length + intercept)];
            double[] rsquared = new double[length];
            double[] cdr = new double[length];
            double[] cr = new double[length];

            final int cdrStart = Math.max(start, period);
            
            for (int i = cdrStart + 1; i < stockHistoryServer.size(); i++) {
                double[] data = new double[period * (nvars + 1)];
                for (int k = 0; k < period; k++) {
                    final long timestamp1 = stockHistoryServer.getTimestamp(i + k + 1 - period);
                    Stock stock1 = stockHistoryServer.getStock(timestamp1);
                    final long timestamp2 = stockHistoryServer.getTimestamp(i + k - period);
                    Stock stock2 = stockHistoryServer.getStock(timestamp2);                    
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
                    OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
                    regression.setNoIntercept(intercept == 0);
                    regression.newSampleData(data, period, nvars);
                    double[] p = regression.estimateRegressionParameters();
                    assert(p.length == refHistoryServers.length + (regression.isNoIntercept() ? 0 : 1));
                    double[] residuals = regression.estimateResiduals();
                    if (i == cdrStart + 1) {
                        double rs = regression.calculateAdjustedRSquared();
                        for (int j = cdrStart + 1; j > start - 1; j--) {
                            dates[j - start] = stockHistoryServer.getTimestamp(j);
                            real[j - start] = data[(j - cdrStart - 1 + period - 1) * (nvars + 1)];
                            delta[j - start] = residuals[j - cdrStart - 1 + period - 1];
                            System.arraycopy(p, 0, params, (j - start) * (refHistoryServers.length + intercept), refHistoryServers.length + intercept);
                            rsquared[j - start] = rs;
                        }
                    } else {
                        dates[i - start] = stockHistoryServer.getTimestamp(i);
                        real[i - start] = data[(period - 1) * (nvars + 1)];
                        delta[i - start] = residuals[residuals.length - 1];
                        System.arraycopy(p, 0, params, (i - start) * (refHistoryServers.length + intercept), refHistoryServers.length + intercept);
                        rsquared[i - start] = regression.calculateAdjustedRSquared();
                    }
                } catch (Exception ex) {
                    delta[i - start] = 0.0;
                }
            }
                        
            cdr[0] = delta[0];
            cr[0] = real[0];
            for (int i = 1; i < cdr.length; i++) {
                cdr[i] = cdr[i - 1] + delta[i];
                cr[i] = cr[i - 1] + real[i];
            }
            
            for (int i = 0; i < cdr.length; i++) {
                String[] row = new String[8 + refHistoryServers.length * 2 + intercept];
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(dates[i]);
                
                row[0] = String.format("%1$tY/%1$tm/%1td", calendar);
                row[1] = String.valueOf(stockHistoryServer.getStock(dates[i]).getLastPrice());

                for (int j = 0; j < refHistoryServers.length; j++) {
                    Stock ref = refHistoryServers[j].getStock(dates[i]);
                    if (ref == null) {
                        row[2 + j] = "N/A";
                    } else {
                        row[2 + j] = String.valueOf(ref.getLastPrice());
                    }
                }
                
                row[2 + refHistoryServers.length] = String.valueOf(real[i]);
                
                for (int j = 0; j < refHistoryServers.length + intercept; j++) {
                    row[3 + refHistoryServers.length + j] = String.valueOf(params[i * (refHistoryServers.length + intercept) + j]);
                }

                row[3 + refHistoryServers.length * 2 + intercept] = String.valueOf(Math.sqrt(rsquared[i]));
                row[4 + refHistoryServers.length * 2 + intercept] = String.valueOf(real[i] - delta[i]);
                row[5 + refHistoryServers.length * 2 + intercept] = String.valueOf(delta[i]);
                row[6 + refHistoryServers.length * 2 + intercept] = String.valueOf(cdr[i]);
                row[6 + refHistoryServers.length * 2 + intercept] = String.valueOf(cr[i]);
                csvwriter.writeNext(row);
            }
            
            csvwriter.flush();
        } catch (IOException ex) {
            System.console().printf(ex.getMessage());
        } finally {
            if (csvwriter != null) {
                try {
                    csvwriter.close();
                } catch (IOException ex) {
                    System.console().printf(ex.getMessage());
                }
            }
            org.yccheok.jstock.gui.Utils.close(outputStreamWriter);
            org.yccheok.jstock.gui.Utils.close(fileOutputStream);
        }

    }
}
