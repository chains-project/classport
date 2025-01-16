package com.example;

import org.apache.commons.lang3.StringUtils;

public class Main {
    public static void main(String[] args)  throws InterruptedException {
        System.out.println(StringUtils.capitalize("Hello world!"));
        customSleepingThread();
        CustomClassGson.CustomClassGsonSleep();
        System.out.println(StringUtils.capitalize("Hello world2!"));
        for (int i =0; i<100000; ++i) {
            print();
            Thread.sleep(5000);
        }
    }

    static void print() {
        System.out.println("Hello world4!");
    }

    private static void customSleepingThread() {
        try { Thread.sleep(4000); } catch (InterruptedException e) { }
    }
}