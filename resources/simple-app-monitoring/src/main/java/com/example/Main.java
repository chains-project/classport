package com.example;

import org.apache.commons.lang3.StringUtils;

public class Main {
    public static void main(String[] args) {
        System.out.println(StringUtils.capitalize("Hello world!"));
        customSleepingThread();
        CustomClassGson.CustomClassGsonSleep();
        System.out.println(StringUtils.capitalize("Hello world2!"));
    }

    private static void customSleepingThread() {
        try { Thread.sleep(4000); } catch (InterruptedException e) { }
    }
}