package com.owlaser.paclist.service;

import com.owlaser.paclist.dao.PacDao;
import com.owlaser.paclist.entity.Dependency;
import fr.dutra.tools.maven.deptree.core.Node;
import org.apache.maven.shared.invoker.*;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 获得依赖信息的函数
 */

@Service
public class PacService {
    @Autowired
    private PacDao pacDao;

    @Autowired
    private Http2GetService http2GetService;

    /**
     *运行maven命令，得到依赖树txt文件并返回txt的路径
     */
    public String CreateDependencyText(String folderPath, Path pomPath) {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(String.valueOf(pomPath)));
        String textPath = folderPath + "dependency_tree";
        request.setGoals(Collections.singletonList("dependency:tree -D outputFile=dependency_tree -D outputType=text"));
        Invoker invoker = new DefaultInvoker();
        // 指定本机的MAVEN_HOME地址，参考invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
        invoker.setMavenHome(new File(System.getenv("MAVEN_HOME")));
        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            e.printStackTrace();
        }
        return textPath;
    }


    /**
     * 得到pom文件里的依赖包信息
     */
    public void GetDependencies(Node root, List<Dependency> dependenciesList){
        for(int i = 0; i < root.getChildNodes().size(); i++)
        {
            Dependency dependency = new Dependency();
            dependency.setGroup_id(root.getChildNode(i).getGroupId());
            dependency.setArtifact_id(root.getChildNode(i).getArtifactId());
            dependency.setVersion(root.getChildNode(i).getVersion());

            //若数据库有则返回数据库数据，若没有则爬下来加入数据库
            if(dependency.getArtifact_id() != null && dependency.getVersion() != null){
                String SqlArtifactId = pacDao.getArtifactId(dependency.getArtifact_id(), dependency.getGroup_id());
                if(SqlArtifactId == null){
                    String url = "https://mvnrepository.com/artifact/" + dependency.getGroup_id() + "/" + dependency.getArtifact_id();
                    http2GetService.getAll(url, dependency);
                    dependenciesList.add(dependency);
                    pacDao.insert(dependency);
                }
                else{
                    Dependency SqlDependency = pacDao.getSqlDependency(dependency.getArtifact_id(), dependency.getGroup_id());
                    SqlDependency.setVersion(dependency.getVersion());
                    dependenciesList.add(SqlDependency);
                }

                String sqlParentArtifact_id = pacDao.getParentArtifactId(dependency.getArtifact_id(), dependency.getGroup_id());
                if(sqlParentArtifact_id == null){
                    Node parentNode = root.getChildNode(i);
                    for(int child = 0; child < parentNode.getChildNodes().size(); child++){
                        String url = "https://mvnrepository.com/artifact/" + parentNode.getChildNode(child).getGroupId() + "/" + parentNode.getChildNode(child).getArtifactId();
                        pacDao.insertChild(parentNode.getGroupId(), parentNode.getArtifactId(), parentNode.getChildNode(child).getGroupId(), parentNode.getChildNode(child).getArtifactId(), url);
                    }
                }
            }
        }
        return;
    }

    /**
     *在所用url中得到依赖包的所有参数（版本号，热度等）
     */
    public void getAll(String url, Dependency dependency){
        String tmp="";
        ArrayList<String> versionList= new ArrayList<>();
        ArrayList<Integer> usageList = new ArrayList<>();
        ArrayList<String> licenseList = new ArrayList<>();
        try {

            Connection conn = Jsoup.connect(url).timeout(5000);
//            conn.header("cookie","_ga=GA1.2.1305858049.1572185055; __gads=Test; __cfduid=d4ed591bc6767fb8c03529ea441b656891618574502; SL_GWPT_Show_Hide_tmp=1; SL_wptGlobTipTmp=1; _gid=GA1.2.1088741524.1618814695; MVN_SESSION=eyJhbGciOiJIUzI1NiJ9.eyJkYXRhIjp7InVpZCI6ImE4NjgxYzkxLWY4YzItMTFlOS04YWU2LTZkMDdlMDAzMmQ1NCJ9LCJleHAiOjE2NTAzNTcxODcsIm5iZiI6MTYxODgyMTE4NywiaWF0IjoxNjE4ODIxMTg3fQ.dKuKkC_ugmVX7uaEa8UwIMQa1E9FlByPCLswc4HjodU; _gat=1; __cf_bm=1f05273e4a67f4c9f13708e1f058ae73844a49ca-1618821188-1800-AYFCIDTAJ3wLh9dLRqziPdUiDPcxOnGzDyciwMfr3FXEnENosmQdN32p/nQLvR0H8Xk28wcVCD5ekLXjjcogIicI/sip7EoAQrVws4IOV7TBBnnnGkQKYpOFqKvb/LP8rQ==") ;
            conn.header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            conn.header("accept-encoding", "gzip, deflate, br");
            conn.header("accept-language", "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6");
            conn.header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36");
//            conn.header("cache-control", "max-age=0");
//            conn.header("sec-fetch-dest", "document");
//            conn.header("sec-fetch-mode", "navigate");
//            conn.header("sec-fetch-site", "none");
//            conn.header("sec-fetch-user", "?1");
//            conn.header("upgrade-insecure-requests", "1");
            org.jsoup.nodes.Document document = conn.get();
            Elements latestversionValue = document.getElementsByTag("td");
            Elements versionnameValue = document.select(".vbtn");
            Elements usageValue = document.getElementsByTag("td");
            Elements licenseValue = document.select(".b").select(".lic");

            //找到得到新版本号
            for(org.jsoup.nodes.Element element : latestversionValue) {
                System.out.println(element);
                if (element.text().matches("^\\d{1,3}\\..*$") || element.text().matches("^\\d{1,3}$")) {
                    tmp = element.text();
                    String ragex = "[^(a-zA-Z)]";
                    String stableSign = tmp.replaceAll(ragex, "");////提取版本号中的字母部分，以查看是否是稳定版本
                    if (stableSign.equals("Final")|| stableSign.equals("RELEASE") || stableSign.equals("") ) {
                        dependency.setStable_version(tmp);
                        break;
                    }
                }
            }

            //得到版本号数组
            for(org.jsoup.nodes.Element element:versionnameValue){
                org.jsoup.nodes.Document elementdoc = Jsoup.parse(element.toString());
                Elements versionName = elementdoc.select("a");
                versionList.add(versionName.text());

            }

            //得到热度数组
            for(org.jsoup.nodes.Element element : usageValue){
                if(element.text().matches("\\d{1,3}(,\\d{3})*$")){ //取使用量，对于三位分割法去掉中间的逗号
                    String rawString = element.text();
                    String removeStr = ",";
                    rawString = rawString.replace(removeStr,"");
                    usageList.add(Integer.parseInt(rawString));
                }
            }

            //得到license数组
            for(org.jsoup.nodes.Element element:licenseValue){
                org.jsoup.nodes.Document elementdoc = Jsoup.parse(element.toString());
                Elements license = elementdoc.select("span");
                licenseList.add(license.text());
            }


        }
        catch (IOException e){
            e.printStackTrace();
        }
        dependency.setVersionList(versionList);
        dependency.setUsageList(usageList);
        String license = String.join("  ",licenseList);
        // dependency.setLicenseList(licenseList);
        dependency.setLicense(license);
        String bestVersion = versionList.get(usageList.indexOf(Collections.max(usageList)));
        dependency.setPopular_version(bestVersion);
    }

    /**
     * 读取jar包
     * 获得jar包中的pom文件的字节流数组
     */
    public static byte[] JarRead(String filepath) throws IOException{

        //创造一个input实例，内容不重要
        InputStream input = new InputStream() {
            @Override
            public int read() {
                return -1;  // end of stream
            }
        };
        JarFile jarFile = new JarFile(filepath);
        Enumeration enu = jarFile.entries();
        while(enu.hasMoreElements()){
            String pompath = "";
            JarEntry element = (JarEntry) enu.nextElement();

            //获取Jar包中的条目名字
            String name = element.getName();
            Pattern r = Pattern.compile("(pom.xml)$");
            Matcher m = r.matcher(name);
            if(m.find()){
                pompath = name;
                JarEntry entry = jarFile.getJarEntry(pompath);

                //获取该文件的输入流
                input = jarFile.getInputStream(entry);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n = 0;
                while (-1 != (n = input.read(buffer))) {
                    output.write(buffer, 0, n);
                }
                return output.toByteArray();
            }
        }
        return new byte[0];
    }

    /**
     * 获得当前包下的所有依赖包信息
     */
    public List getParentDependencies(String groupId, String artifactId){
        return pacDao.getParentDependencies(artifactId, groupId);
    }

    /**
     * 获得漏洞信息
     */
    public List getSecurityAdvise(String name){
        return pacDao.getSecurityMessage(name);
    }
}
