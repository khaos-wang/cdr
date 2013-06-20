/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.yccheok.jstock.cdr;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Vector;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.yccheok.jstock.engine.SimpleDate;
import org.yccheok.jstock.engine.StockHistoryServer;
import org.yccheok.jstock.engine.StockInfo;
import org.yccheok.jstock.engine.StockInfoDatabase;
import org.yccheok.jstock.gui.JStockOptions;
import org.yccheok.jstock.gui.MainFrame;

/**
 *
 * @author Jack
 */
public class CDRSettings {

    public static CDRSettings globalSettings = null;
    private static final Log log = LogFactory.getLog(CDRSettings.class);

    static {
        globalSettings = new CDRSettings();
        globalSettings.load();
    }

    public static String getCDRRootDir() {
        final JStockOptions jStockOptions = MainFrame.getInstance().getJStockOptions();
        return org.yccheok.jstock.gui.Utils.getUserDataDirectory() + jStockOptions.getCountry() + File.separator + "cdr" + File.separator;
    }
    public Vector<StockInfo> references = new Vector<StockInfo>();
    public SimpleDate baseDate = new SimpleDate(2009, 0, 1);

    @Override
    public CDRSettings clone() {
        CDRSettings cloned = new CDRSettings();

        cloned.references.addAll(this.references);
        return cloned;
    }

    public void addReference(StockInfo stockInfo) {
        for (int i = 0; i < references.size(); i++) {
            if (this.references.get(i).equals(stockInfo)) {
                return;
            }
        }

        this.references.add(stockInfo);
    }

    public String[] getList() {
        String[] list = new String[this.references.size()];
        for (int i = 0; i < this.references.size(); i++) {
            list[i] = this.references.get(i).symbol.toString() + " - " + this.references.get(i).code.toString();
        }

        return list;
    }

    public void clear() {
        this.references.clear();
    }

    private String getSettingFilePath() {
        return getCDRRootDir() + "cdr.csv";
    }

    public void load() {
        File file = new File(getSettingFilePath());
        FileInputStream fileInputStream = null;
        InputStreamReader inputStreamReader = null;
        CSVReader csvreader = null;

        try {
            fileInputStream = new FileInputStream(file);
            inputStreamReader = new InputStreamReader(fileInputStream, Charset.forName("UTF-8"));
            csvreader = new CSVReader(inputStreamReader);

            String[] next = csvreader.readNext();
            int referenceSize = Integer.parseInt(next[0]);
            StockInfoDatabase sid = MainFrame.getInstance().getStockInfoDatabase();
            for (int i = 0; i < referenceSize; i++) {
                next = csvreader.readNext();
                StockInfo stockInfo = sid.searchStockInfo(next[0]);
                if (stockInfo == null) {
                    throw new IOException("Can't find reference stock " + next[0]);
                }
                this.references.add(stockInfo);
                StockHistoryServer history = MainFrame.getInstance().getStockHistoryServer(stockInfo.code);
                if (history == null) {
                    MainFrame.getInstance().getStockHistoryMonitor().addStockCode(stockInfo.code);
                }
            }

        } catch (IOException ex) {
            log.error(null, ex);
        } finally {
            if (csvreader != null) {
                try {
                    csvreader.close();
                } catch (IOException ex) {
                    log.error(null, ex);
                }
            }
            org.yccheok.jstock.gui.Utils.close(inputStreamReader);
            org.yccheok.jstock.gui.Utils.close(fileInputStream);
        }
    }

    public void save() {
        File file = new File(getSettingFilePath());
        FileOutputStream fileOutputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        CSVWriter csvwriter = null;

        try {
            fileOutputStream = new FileOutputStream(file);
            outputStreamWriter = new OutputStreamWriter(fileOutputStream, Charset.forName("UTF-8"));
            csvwriter = new CSVWriter(outputStreamWriter);

            csvwriter.writeNext(new String[]{String.valueOf(this.references.size())});
            for (int i = 0; i < this.references.size(); i++) {
                csvwriter.writeNext(new String[]{this.references.get(i).code.toString()});
            }

            csvwriter.flush();
        } catch (IOException ex) {
            log.error(null, ex);
        } finally {
            if (csvwriter != null) {
                try {
                    csvwriter.close();
                } catch (IOException ex) {
                    log.error(null, ex);
                }
            }
            org.yccheok.jstock.gui.Utils.close(outputStreamWriter);
            org.yccheok.jstock.gui.Utils.close(fileOutputStream);
        }
    }
}
