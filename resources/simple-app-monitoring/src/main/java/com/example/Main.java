package com.example;

import org.apache.commons.lang3.StringUtils;

@Deprecated
public class Main {
    public static void main(String[] args)  throws InterruptedException {
        System.out.println(StringUtils.capitalize("Hello world!!"));
        customSleepingThread();
        CustomClassGson.CustomClassGsonSleep();
        System.out.println(StringUtils.capitalize("Hello world2!"));
        for (long i =0; i<2; ++i) {
            print();

        }
    }

    static void print() {
        StringUtils.capitalize("Hello world!");
        try { Thread.sleep(10); } catch (InterruptedException e) { }
    }

    private static void customSleepingThread() {
        try { Thread.sleep(4000); } catch (InterruptedException e) { }
    }
}