package com.mycompany.chargingportal;

import java.io.Serializable;

public class User implements Serializable {

    private String msisdn;
    private double balance;

    public User() {
    }

    public User(String msisdn, double balance) {
        this.msisdn = msisdn;
        this.balance = balance;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
