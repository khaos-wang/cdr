/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.yccheok.jstock.gui.cdr;

import java.io.File;
import java.util.Vector;
import org.yccheok.jstock.engine.StockInfo;
import org.yccheok.jstock.gui.JStockOptions;
import org.yccheok.jstock.gui.MainFrame;

/**
 *
 * @author Jack
 */
public class CDRSettings {
    public static CDRSettings instance = null;
    static {
        instance = new CDRSettings();
        instance.load();
    }
    
    public int period;
    public Vector<StockInfo> references = new Vector<StockInfo>();
    
    public CDRSettings clone() {
        CDRSettings cloned = new CDRSettings();
        cloned.period = this.period;
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
        final JStockOptions jStockOptions = MainFrame.getInstance().getJStockOptions();
        return org.yccheok.jstock.gui.Utils.getUserDataDirectory() + jStockOptions.getCountry() + File.separator + "cdr.xml";
    }
    public void load() {
        
    }
    
    public void save() {
        
    }
}
