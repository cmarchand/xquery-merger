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
public interface AppenderFilter {
    public AppenderFilter ACCEPT_ALL = new AppenderFilter() {
        @Override
        public boolean accept(String s) {
            return true;
        }
    };
    /**
     * Returns <tt>true</tt> if <tt>s</tt> can be appended
     * @param s
     * @return 
     */
    boolean accept(String s);
}
