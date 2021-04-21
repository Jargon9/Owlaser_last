package com.owlaser.paclist.service;

import com.owlaser.paclist.entity.Dependency;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;

@Service
public class Http2GetService {

    public  void getAll(String url, Dependency dependency ){
        String tmp="";
        ArrayList<String> versionList= new ArrayList<>();
        ArrayList<Integer> usageList = new ArrayList<>();
        ArrayList<String> licenseList = new ArrayList<>();
        WebDriver driver = null;
        try {
            System.setProperty("webdriver.chrome.driver", "D:\\pku\\driver\\chromedriver.exe");
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--headless");
            driver = new ChromeDriver(chromeOptions);
            driver.get(url);
            String pageSource = driver.getPageSource();
            org.jsoup.nodes.Document document = Jsoup.parse(pageSource);
            Elements latestversionValue = document.getElementsByTag("td");
            Elements versionnameValue = document.select(".vbtn");
            Elements usageValue = document.getElementsByTag("td");
            Elements licenseValue = document.select(".b").select(".lic");

            for (org.jsoup.nodes.Element element : latestversionValue) {
                System.out.println(element);
                if (element.text().matches("^\\d{1,3}\\..*$") || element.text().matches("^\\d{1,3}$")) {
                    tmp = element.text();
                    String ragex = "[^(a-zA-Z)]";
                    String stableSign = tmp.replaceAll(ragex, "");////提取版本号中的字母部分，以查看是否是稳定版本
                    if (stableSign.equals("Final") || stableSign.equals("RELEASE") || stableSign.equals("")) {
                        dependency.setStable_version(tmp);
                        break;
                    }
                }
            }

            //得到版本号数组
            for (org.jsoup.nodes.Element element : versionnameValue) {
                org.jsoup.nodes.Document elementdoc = Jsoup.parse(element.toString());
                Elements versionName = elementdoc.select("a");
                versionList.add(versionName.text());
            }

            //得到热度数组
            for (org.jsoup.nodes.Element element : usageValue) {
                if (element.text().matches("\\d{1,3}(,\\d{3})*$")) { //取使用量，对于三位分割法去掉中间的逗号
                    String rawString = element.text();
                    String removeStr = ",";
                    rawString = rawString.replace(removeStr, "");
                    usageList.add(Integer.parseInt(rawString));
                }
            }

            //得到license数组
            for (org.jsoup.nodes.Element element : licenseValue) {
                org.jsoup.nodes.Document elementdoc = Jsoup.parse(element.toString());
                Elements license = elementdoc.select("span");
                licenseList.add(license.text());
            }
        }  finally {
            if (driver != null) {
                driver.close();
            }
        }

        dependency.setVersionList(versionList);
        dependency.setUsageList(usageList);
        String license = String.join("  ",licenseList);
        // dependency.setLicenseList(licenseList);
        dependency.setLicense(license);
        String bestVersion = versionList.get(usageList.indexOf(Collections.max(usageList)));
        dependency.setPopular_version(bestVersion);
    }
}
