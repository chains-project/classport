package com.example;

import org.apache.commons.lang3.StringUtils;

// import com.google.gson.Gson;

public class CustomClassGson {
    public static void customClassGsonSleep() {
       //Gson gson = new Gson();
        //System.out.println(gson.toJson(1));
        System.out.println(StringUtils.capitalize("Hello world!3"));
        try { Thread.sleep(10000); } catch (InterruptedException e) { }
    }
       
}
