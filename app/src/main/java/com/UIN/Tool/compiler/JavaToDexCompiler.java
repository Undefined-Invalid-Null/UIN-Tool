package com.UIN.Tool.compiler;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import com.UIN.Tool.utils.LogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class JavaToDexCompiler {
    
    private Context context;
    private OnCompileListener listener;
    private boolean useFullCompile = false;
    private String uiType = "native"; // native 或 web
    
    private static final String ECJ_JAR_ASSET = "compiler/ecj.jar";
    private static final String D8_JAR_ASSET = "compiler/d8.jar";
    
    public interface OnCompileListener {
        void onStart();
        void onProgress(String message);
        void onSuccess(File resultFile);
        void onError(String error);
    }
    
    public JavaToDexCompiler(Context context) { 
        this.context = context; 
    }
    
    public void setOnCompileListener(OnCompileListener listener) { 
        this.listener = listener; 
    }
    
    public void setUseFullCompile(boolean useFullCompile) {
        this.useFullCompile = useFullCompile;
    }
    
    public void setUiType(String uiType) {
        this.uiType = uiType;
    }
    
    public void compile(File javaSrcDir, File outputDir, String classpath) {
        new CompileTask(javaSrcDir, outputDir, classpath).execute();
    }
    
    public void packageToTpk(File projectDir, File outputTpk) {
        new PackageTask(projectDir, outputTpk).execute();
    }
    
    private class CompileTask extends AsyncTask<Void, String, Boolean> {
        private File javaSrcDir, outputDir, classOutputDir, dexOutputFile, jarFile;
        private String classpath, errorMessage;
        private long startTime;
        
        CompileTask(File javaSrcDir, File outputDir, String classpath) {
            this.javaSrcDir = javaSrcDir;
            this.outputDir = outputDir;
            this.classpath = classpath;
            this.classOutputDir = new File(outputDir, "classes");
            this.dexOutputFile = new File(outputDir, "classes.dex");
            this.jarFile = new File(outputDir, "input.jar");
        }
        
        @Override protected void onPreExecute() { 
            startTime = System.currentTimeMillis();
            if (listener != null) listener.onStart(); 
            LogUtils.enter("JavaToDexCompiler", "compile");
        }
        
        @Override protected Boolean doInBackground(Void... voids) {
            if (useFullCompile) {
                publishProgress("准备编译环境...");
                if (!compileJavaToClass()) return false;
                publishProgress("打包 JAR...");
                if (!packageJar()) return false;
                publishProgress("生成 DEX...");
                if (!convertJarToDex()) return false;
            } else {
                publishProgress("生成项目文件...");
                generateProjectFiles();
            }
            return true;
        }
        
        private boolean compileJavaToClass() {
            try {
                File ecjJar = copyAssetToCache(ECJ_JAR_ASSET, "ecj.jar");
                if (ecjJar == null) throw new Exception("ECJ 编译器未找到");
                
                URLClassLoader loader = new URLClassLoader(new URL[]{ecjJar.toURI().toURL()}, getClass().getClassLoader());
                Class<?> mainClass = loader.loadClass("org.eclipse.jdt.internal.compiler.batch.Main");
                
                List<String> args = new ArrayList<>();
                args.add("-d"); args.add(classOutputDir.getAbsolutePath());
                args.add("-sourcepath"); args.add(javaSrcDir.getAbsolutePath());
                args.add("-source"); args.add("1.7"); args.add("-target"); args.add("1.7");
                args.add("-noExit"); args.add("-proceedOnError");
                if (classpath != null && !classpath.isEmpty()) { 
                    args.add("-classpath"); 
                    args.add(classpath); 
                }
                
                List<File> javaFiles = new ArrayList<>();
                findJavaFiles(javaSrcDir, javaFiles);
                for (File f : javaFiles) args.add(f.getAbsolutePath());
                
                Object compiler = mainClass.getConstructor(java.io.PrintWriter.class, java.io.PrintWriter.class, boolean.class)
                        .newInstance(new java.io.PrintWriter(System.out), new java.io.PrintWriter(System.err), false);
                Method compileMethod = mainClass.getMethod("compile", String[].class);
                boolean success = (Boolean) compileMethod.invoke(compiler, (Object) args.toArray(new String[0]));
                
                if (!success) throw new Exception("ECJ 编译失败");
                return true;
            } catch (Exception e) {
                errorMessage = "ECJ 编译异常: " + e.getMessage();
                LogUtils.ex("JavaToDexCompiler", e);
                return false;
            }
        }
        
        private boolean packageJar() {
            try {
                ProcessBuilder pb = new ProcessBuilder("jar", "cf", jarFile.getAbsolutePath(), "-C", classOutputDir.getAbsolutePath(), ".");
                int exitCode = pb.start().waitFor();
                if (exitCode != 0) throw new Exception("jar 打包失败");
                return true;
            } catch (Exception e) {
                errorMessage = "jar 打包异常: " + e.getMessage();
                return false;
            }
        }
        
        private boolean convertJarToDex() {
            try {
                File d8Jar = copyAssetToCache(D8_JAR_ASSET, "d8.jar");
                if (d8Jar == null) throw new Exception("D8 编译器未找到");
                
                URLClassLoader loader = new URLClassLoader(new URL[]{d8Jar.toURI().toURL()}, getClass().getClassLoader());
                Class<?> d8Class = loader.loadClass("com.android.tools.r8.D8");
                Method mainMethod = d8Class.getMethod("main", String[].class);
                
                List<String> args = new ArrayList<>();
                args.add("--min-api"); args.add("21");
                args.add("--output"); args.add(outputDir.getAbsolutePath());
                args.add(jarFile.getAbsolutePath());
                
                mainMethod.invoke(null, (Object) args.toArray(new String[0]));
                if (!dexOutputFile.exists()) throw new Exception("D8 未生成 DEX 文件");
                return true;
            } catch (Exception e) {
                errorMessage = "D8 转换异常: " + e.getMessage();
                LogUtils.ex("JavaToDexCompiler", e);
                return false;
            }
        }
        
        private void generateProjectFiles() {
            try {
                if (!classOutputDir.exists()) classOutputDir.mkdirs();
                File readmeFile = new File(outputDir, "README.md");
                String readme = "# 插件编译说明\n\n" +
                        "## 手动编译步骤\n\n" +
                        "1. 将 Java 源码复制到电脑\n" +
                        "2. 使用 javac 编译: javac -d . -cp host-sdk.jar src/**/*.java\n" +
                        "3. 打包为 jar: jar cvf plugin.jar com/\n" +
                        "4. 转换为 dex: d8 --lib android.jar --min-api 21 --output . plugin.jar\n" +
                        "5. 重命名: mv classes.dex plugin.dex\n";
                FileOutputStream fos = new FileOutputStream(readmeFile);
                fos.write(readme.getBytes());
                fos.close();
                LogUtils.success("JavaToDexCompiler", "项目文件已生成: " + outputDir);
            } catch (Exception e) {
                LogUtils.ex("JavaToDexCompiler", e);
            }
        }
        
        private File copyAssetToCache(String assetPath, String cacheName) {
            try {
                File cacheFile = new File(context.getCacheDir(), cacheName);
                if (cacheFile.exists()) return cacheFile;
                try (java.io.InputStream is = context.getAssets().open(assetPath);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                }
                return cacheFile;
            } catch (Exception e) { 
                LogUtils.e("JavaToDexCompiler", "复制资产失败: " + assetPath, e);
                return null; 
            }
        }
        
        private void findJavaFiles(File dir, List<File> result) {
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.isDirectory()) findJavaFiles(f, result);
                else if (f.getName().endsWith(".java")) result.add(f);
            }
        }
        
        @Override protected void onProgressUpdate(String... values) { 
            if (listener != null) listener.onProgress(values[0]); 
        }
        
        @Override protected void onPostExecute(Boolean success) { 
            LogUtils.exit("JavaToDexCompiler", "compile", startTime);
            if (success) {
                if (listener != null) listener.onSuccess(dexOutputFile);
            } else {
                if (listener != null) listener.onError(errorMessage != null ? errorMessage : "编译失败");
            }
        }
    }
    
    /**
     * 打包项目为 TPK 文件
     * 原生插件：包含 plugin.json, icon.png, plugin.dex, src/, res/
     * Web 插件：包含 plugin.json, icon.png, web/
     */
    private class PackageTask extends AsyncTask<Void, String, Boolean> {
        private File projectDir, outputTpk;
        private String errorMessage;
        
        PackageTask(File projectDir, File outputTpk) { 
            this.projectDir = projectDir; 
            this.outputTpk = outputTpk; 
        }
        
        @Override protected Boolean doInBackground(Void... voids) { 
            return doPackage(); 
        }
        
        private boolean doPackage() {
            try {
                if (outputTpk.exists()) {
                    outputTpk.delete();
                }
                
                File parentDir = outputTpk.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // 读取 plugin.json 判断插件类型
                String pluginUiType = "native";
                File jsonFile = new File(projectDir, "plugin.json");
                if (jsonFile.exists()) {
                    String json = readFileToString(jsonFile);
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        pluginUiType = obj.optString("uiType", "native");
                    } catch (Exception e) {
                        LogUtils.e("JavaToDexCompiler", "解析 plugin.json 失败", e);
                    }
                }
                
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputTpk));
                
                // 1. 添加 plugin.json
                addFileToZip(zos, jsonFile, "plugin.json");
                
                // 2. 添加 icon.png
                addFileToZip(zos, new File(projectDir, "icon.png"), "icon.png");
                
                // 3. 根据插件类型处理
                if ("web".equals(pluginUiType)) {
                    // Web 插件：不需要 plugin.dex，只需要 web 目录
                    LogUtils.i("JavaToDexCompiler", "打包 Web 插件，跳过 DEX 文件");
                    
                    // 添加 web 目录
                    File webDir = new File(projectDir, "web");
                    if (webDir.exists() && webDir.isDirectory()) {
                        LogUtils.i("JavaToDexCompiler", "检测到 web 目录，正在打包 Web 资源...");
                        addDirToZip(zos, webDir, "web/");
                    } else {
                        LogUtils.w("JavaToDexCompiler", "未检测到 web 目录，创建默认页面");
                        // 创建默认的 web 目录和 index.html
                        ZipEntry entry = new ZipEntry("web/index.html");
                        zos.putNextEntry(entry);
                        String defaultHtml = getDefaultHtml();
                        zos.write(defaultHtml.getBytes());
                        zos.closeEntry();
                    }
                } else {
                    // 原生插件：需要 plugin.dex
                    File dexFile = new File(projectDir, "plugin.dex");
                    if (dexFile.exists()) {
                        addFileToZip(zos, dexFile, "plugin.dex");
                    } else {
                        LogUtils.w("JavaToDexCompiler", "原生插件缺少 plugin.dex，创建占位文件");
                        ZipEntry entry = new ZipEntry("plugin.dex");
                        zos.putNextEntry(entry);
                        zos.write("// 占位文件，请替换为实际编译的 DEX".getBytes());
                        zos.closeEntry();
                    }
                    
                    // 添加 src 目录
                    File srcDir = new File(projectDir, "src");
                    if (srcDir.exists()) {
                        addDirToZip(zos, srcDir, "src/");
                    }
                    
                    // 添加 res 目录
                    File resDir = new File(projectDir, "res");
                    if (resDir.exists() && resDir.listFiles() != null && resDir.listFiles().length > 0) {
                        addDirToZip(zos, resDir, "res/");
                    }
                }
                
                zos.close();
                LogUtils.success("JavaToDexCompiler", "TPK 打包完成: " + outputTpk.getAbsolutePath());
                return true;
                
            } catch (Exception e) { 
                errorMessage = "打包失败: " + e.getMessage(); 
                LogUtils.ex("JavaToDexCompiler", e);
                return false; 
            }
        }
        
        private String getDefaultHtml() {
            return "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>Web 插件</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: sans-serif; padding: 20px; text-align: center; }\n" +
                    "        button { background: #37474F; color: white; border: none; padding: 12px 24px; border-radius: 8px; margin: 10px; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Web 插件</h1>\n" +
                    "    <button onclick=\"UINPlugin.callHost('toast', 'Hello from Web Plugin!')\">点我</button>\n" +
                    "    <button onclick=\"UINPlugin.callHost('finish', '')\">关闭</button>\n" +
                    "    <script>\n" +
                    "        console.log('Web 插件已加载');\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";
        }
        
        private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws Exception {
            if (file.exists() && file.isFile()) {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                fis.close();
                zos.closeEntry();
                LogUtils.d("JavaToDexCompiler", "已添加: " + entryName + " (" + file.length() + " bytes)");
            } else {
                LogUtils.w("JavaToDexCompiler", "文件不存在: " + entryName);
            }
        }
        
        private void addDirToZip(ZipOutputStream zos, File dir, String basePath) throws Exception {
            if (!dir.exists() || !dir.isDirectory()) return;
            
            File[] files = dir.listFiles();
            if (files == null) return;
            
            for (File file : files) {
                String entryName = basePath + file.getName();
                if (file.isDirectory()) {
                    // 添加目录条目
                    ZipEntry entry = new ZipEntry(entryName + "/");
                    zos.putNextEntry(entry);
                    zos.closeEntry();
                    // 递归处理子目录
                    addDirToZip(zos, file, entryName + "/");
                } else {
                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    FileInputStream fis = new FileInputStream(file);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    fis.close();
                    zos.closeEntry();
                    LogUtils.d("JavaToDexCompiler", "已添加: " + entryName + " (" + file.length() + " bytes)");
                }
            }
        }
        
        private String readFileToString(File file) {
            try {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                return new String(data, "UTF-8");
            } catch (Exception e) {
                return "{}";
            }
        }
        
        @Override protected void onPostExecute(Boolean success) { 
            if (success && listener != null) {
                listener.onSuccess(outputTpk);
            } else if (listener != null) {
                listener.onError(errorMessage != null ? errorMessage : "打包失败");
            }
        }
    }
}