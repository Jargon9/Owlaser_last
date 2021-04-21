package com.owlaser.paclist.controller;
import com.owlaser.paclist.entity.*;
import com.owlaser.paclist.entity.Sum_dependency_license;
import com.owlaser.paclist.service.DependencyTreeService;
import com.owlaser.paclist.service.LicenseService;
import com.owlaser.paclist.service.PacService;
import com.owlaser.paclist.util.ResponseUtil;
import fr.dutra.tools.maven.deptree.core.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class PacController {

    @Autowired
    private PacService pacService;

    @Autowired
    private  LicenseService licenseService;

    @ResponseBody
    @PostMapping(value = "/upload")
    public Object upload(@RequestParam("file") MultipartFile file) {
        if(!file.getOriginalFilename().equals("pom.xml") && !file.getOriginalFilename().matches(".*\\.jar") && !file.getOriginalFilename().matches(".*\\.zip")){
            return ResponseUtil.badArgument();
        }

        ArrayList<Dependency> dependenciesList = new ArrayList<>();
        try {
            byte[] bytes = file.getBytes();
            String folderPath = String.format("%s%s%d/", "./repository/pom/", "folderForOwlaser", PacService.count.incrementAndGet());
            File fold = new File(folderPath);
            folderPath = String.format("%s/", fold.getAbsolutePath());
            if (!fold.exists()) {
                fold.mkdir();
            } else {
                /** todo 可能会有错误 */
                fold.delete();
                File newFold = new File(folderPath);
                newFold.mkdir();
            }
            Path filePath = Paths.get(folderPath + file.getOriginalFilename());
            Files.write(filePath, bytes);
            Pattern r = Pattern.compile("(pom.xml)$");
            Matcher m = r.matcher(file.getOriginalFilename());

            if(!m.find()){
                byte[] pomFile = new byte[1];
                if (file.getOriginalFilename().matches(".*\\.jar")) {
                    pomFile = pacService.JarRead(folderPath + file.getOriginalFilename());
                    if(pomFile.length == 1) return ResponseUtil.noPom();
                    Files.write(filePath, pomFile);
                } else {
                    pacService.ZipRead(folderPath + file.getOriginalFilename(), folderPath);
                    filePath = Paths.get(folderPath + "pom.xml");
                }
            }

            List<String> textPaths = pacService.CreateDependencyText(folderPath, filePath);
            for (String textPath : textPaths) {
                Node root = DependencyTreeService.GetRoot(textPath);
                pacService.GetDependencies(root, dependenciesList);
            }
        }  catch (Exception e) {
            e.printStackTrace();
        }
        Sum_dependency_license sum_dependency_license = new Sum_dependency_license(dependenciesList,licenseService.getConflic(dependenciesList));
        return ResponseUtil.ok(sum_dependency_license);
    }

    /**
     * 查询依赖信息接口
     */
    @ResponseBody
    @GetMapping(value = "/getdependency")
    public Object getDependency(String groupId, String artifactId){
        List<ChildNode> childNodes = pacService.getParentDependencies(groupId, artifactId);
        return ResponseUtil.okList(childNodes);
    }

    /**
     * 漏洞查询接口
     */
    @ResponseBody
    @GetMapping(value = "/security")
    public Object getSecurityAdvise(String groupId, String artifactId, String version){
        String name = groupId + ":" + artifactId;
        List<SecurityAdvise> securityAdvises = pacService.getSecurityAdvise(name);
        return ResponseUtil.okListAndVersion(securityAdvises, version);
    }

    /**
     * 运行测试
     */
    @ResponseBody
    @GetMapping(value = "/test")
    public Object runTest(){
        return "运行成功！";
    }
}
