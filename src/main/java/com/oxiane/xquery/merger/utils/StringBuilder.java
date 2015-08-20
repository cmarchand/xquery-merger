/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oxiane.xquery.merger.utils;

/**
 *
 * @author ext-cmarchand
 */
public class StringBuilder {
    java.lang.StringBuilder sb;
    private AppenderFilter filter;
    
    public StringBuilder() {
        this(AppenderFilter.ACCEPT_ALL);
    }
    
    public StringBuilder(AppenderFilter filter) {
        super();
        if(filter==null) throw new IllegalArgumentException("filter must be not null");
        this.filter = filter;
        sb = new java.lang.StringBuilder();
    }
    
    public StringBuilder append(String cs) {
        if(filter.accept(cs)) {
            sb.append(cs);
        }
        return this;
    }
    
    @Override
    public String toString() {
        return sb.toString();
    }
}
