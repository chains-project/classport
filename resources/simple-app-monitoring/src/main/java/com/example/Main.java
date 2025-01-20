package com.example;

import org.apache.commons.lang3.StringUtils;

public class Main {
    public static void main(String[] args)  throws InterruptedException {
        System.out.println(StringUtils.capitalize("Hello world!"));
        customSleepingThread();
        CustomClassGson.CustomClassGsonSleep();
        System.out.println(StringUtils.capitalize("Hello world2!"));
        for (long i =0; i<100000000; ++i) {
            print();

        }
    }

    static void print() {
        StringUtils.capitalize("Hello world!");
    }

    private static void customSleepingThread() {
        try { Thread.sleep(4000); } catch (InterruptedException e) { }
    }
}