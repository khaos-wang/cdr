/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.yccheok.jstock.engine;

import java.io.File;
import java.io.FileFilter;
import java.util.Calendar;
import org.yccheok.jstock.file.Statements;

/**
 *
 * @author Jack
 */
public class SuperStockHistoryServer implements StockHistoryServer {

    private Code code;
    private StockHistoryServer stockHistoryServer;
    private StatementsStockHistoryServer statementsServer;

    public SuperStockHistoryServer(StockServerFactory factory, Code code) throws StockHistoryNotFoundException {
        this(factory, code, Duration.getTodayDurationByYears(10));
    }

    public SuperStockHistoryServer(StockServerFactory factory, Code code, Duration duration) throws StockHistoryNotFoundException {
        this.code = code;
        final String prefix = code.toString();
        final String directory = org.yccheok.jstock.gui.Utils.getHistoryDirectory();
        File dir = new File(directory);
        File[] files = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isFile()) {
                    String name = file.getName();
                    return name.startsWith(prefix.toString()) && name.endsWith(".csv");
                } else {
                    return false;
                }
            }
        });

        if (files.length > 0) {
            File longestHistory = files[0];
            for (int i = 1; i < files.length; i++) {
                if (files[i].length() > longestHistory.length()) {
                    longestHistory = files[i];
                }
            }

            Statements statements = Statements.newInstanceFromCSVFile(longestHistory);
            this.statementsServer = StatementsStockHistoryServer.newInstance(statements);
            final Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(this.statementsServer.getTimestamp(this.statementsServer.size() - 1));
            calendar.add(Calendar.DAY_OF_MONTH, 1);

            final SimpleDate start = new SimpleDate(calendar);
            if (start.compareTo(duration.getEndDate()) < 0) {
                Duration subDuration = new Duration(start, duration.getEndDate());
                this.stockHistoryServer = factory.getStockHistoryServer(code, subDuration);                
            }
        } else {
            this.stockHistoryServer = factory.getStockHistoryServer(code, duration);
        }

        if (this.stockHistoryServer == null && this.statementsServer == null) {
            throw new StockHistoryNotFoundException("Can't get history server");
        }
        
        if (this.stockHistoryServer != null) {
            autoSave();
        }
    }

    @Override
    public Stock getStock(long timestamp) {
        if (this.statementsServer == null) {
            return this.stockHistoryServer.getStock(timestamp);
        }
        if (this.stockHistoryServer == null) {
            return this.statementsServer.getStock(timestamp);
        }

        Stock stock = this.statementsServer.getStock(timestamp);
        if (stock == null) {
            return this.stockHistoryServer.getStock(timestamp);
        } else {
            return stock;
        }
    }

    @Override
    public long getTimestamp(int index) {
        if (this.statementsServer == null) {
            return this.stockHistoryServer.getTimestamp(index);
        }

        if (this.stockHistoryServer == null) {
            return this.statementsServer.getTimestamp(index);
        }

        if (index < this.statementsServer.size()) {
            return this.statementsServer.getTimestamp(index);
        } else {
            return this.stockHistoryServer.getTimestamp(index - this.statementsServer.size());
        }
    }

    @Override
    public int size() {
        if (this.statementsServer == null) {
            return this.stockHistoryServer.size();
        }

        if (this.stockHistoryServer == null) {
            return this.statementsServer.size();
        }

        return this.statementsServer.size() + this.stockHistoryServer.size();
    }

    @Override
    public long getSharesIssued() {
        return this.stockHistoryServer == null ? this.statementsServer.getSharesIssued() : this.stockHistoryServer.getSharesIssued();
    }

    @Override
    public long getMarketCapital() {
        return this.stockHistoryServer == null ? this.statementsServer.getMarketCapital() : this.stockHistoryServer.getMarketCapital();
    }

    private void autoSave() {
        final File file = new File(org.yccheok.jstock.gui.Utils.getHistoryDirectory() + File.separator + this.code.toString() + ".csv");
        final Statements statements = Statements.newInstanceFromStockHistoryServer(this, false);
        statements.saveAsCSVFile(file);
    }
}
