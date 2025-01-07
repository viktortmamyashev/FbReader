package com.example;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;

import javax.imageio.ImageIO;

import org.mozilla.universalchardet.UniversalDetector;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class FbReader extends Application  {
   
   // Путь к тестовому файлу FB2
   private static final String EPUB_FILE = "test.fb2";
   
   public static void main(String[] args) {
      launch(args);
   }

   public void start(Stage stage) throws Exception {
      stage.setTitle("FBReader: программа для чтения книг FB2");
      
      // Создаем временную папку в папке проекта
      String filePath = new File("").getAbsolutePath();
      Path tempDir = Files.createTempDirectory(Paths.get(filePath), "temp");
      
      // Каталог, в который нужно сохранить все ресурсы
      String outputDir = String.valueOf(tempDir) + "\\";
      
      // Создаем копию файла и переименовываем расширение в xml
      try {
         copyFile(new File(EPUB_FILE), new File(outputDir + "temp.txt"));
      } catch (java.nio.file.NoSuchFileException e) {
         Alert alert = new Alert(AlertType.WARNING);
         alert.setTitle("Файл не найден");
         alert.setHeaderText("Ошибка при открытии файла");
         alert.setContentText("Такого файла не существует.");

         alert.showAndWait();

         File tmpFls = new File(String.valueOf(tempDir));
         deleteDir(tmpFls);          
      }  
      
      // Получение всего текста из файла temp.txt
      String tempFilePath = outputDir + "temp.txt";
      String charset = detectCharset(tempFilePath);
      String content = readText(tempFilePath, charset);
      
      // Получение текста между тегами description
      int start = content.indexOf("<description>");
      int end = content.lastIndexOf("</description>");

      end = end + 14;

      char[] dest = new char[end - start];
      content.getChars(start, end, dest, 0);
      String description = new String(dest);
      description = description.replace("image l:href=\"#", "img src=\"file://" + tempDir.toString().replace("\\","/") + "/");
      
      // Получение текста между тегами body      
      int startBody = content.indexOf("<body>");
      int endBody = content.lastIndexOf("</body>");

      endBody = endBody + 7;

      char[] dst = new char[endBody - startBody];
      content.getChars(startBody, endBody, dst, 0);
      String body = new String(dst);
      body = body.replace("image l:href=\"#", "img src=\"file://" + tempDir.toString().replace("\\", "/") + "/");
      
      // Получение текста со всеми картинками (binary)
      int startBin = content.indexOf("<binary");
      int endBin = content.lastIndexOf("</binary>");
      
      if(startBin != -1 || endBin != -1) {

         endBin = endBin + 9;

         char[] dstb = new char[endBin - startBin];
         content.getChars(startBin, endBin, dstb, 0);
         String binContent = new String(dstb);
         //System.out.println(binContent);
      
         // Объявляем массивы для данных о картинках
         ArrayList<String> binCode = new ArrayList<>();
         ArrayList<String> binImg = new ArrayList<>();
      
         // Получение отдельных binary
         do {        
            int nextBin = binContent.indexOf("<binary");
            int lastBin = binContent.indexOf("</binary>");
         
            if(nextBin != -1) {
               lastBin = lastBin + 9;
            
               // Получение первой из оставшихся binary
               char[] dstBin = new char[lastBin - nextBin];
               binContent.getChars(nextBin, lastBin, dstBin, 0);
               String binaryEl = new String(dstBin);
         
               // Удаление текущей картинки из текста со всеми картинками
               binContent = binContent.replace(binaryEl, "");
         
               // Удаление закрывающего тега binary из текущей картинки
               binaryEl = binaryEl.replace("</binary>", "");
         
               // Получение строки с открывающим тегом binary
               int findString = binaryEl.indexOf(">");
               String tag = binaryEl.substring(0,findString + 1);         
         
               // Удаление открываюшего тега из картинки
               binaryEl = binaryEl.replace(tag, "");
         
               // Получение названия картинки
               int findId = tag.indexOf("id=");
               findId = findId + 4;
               String imageName = tag.substring(findId);
               int newFindId = imageName.indexOf("\"");
               String delText = imageName.substring(newFindId);
               imageName = imageName.replace(delText, "");            
            
               // Кладем данные о картинках в массивы
               binCode.add(binaryEl);
               binImg.add(imageName);         
            } else {
               break;
            } 
         
         } while(binContent.indexOf("<binary") != -1);           
      
         // Сохраняем картинки во временную директорию temp
         for(int i = 0; i < binCode.size(); i++) {    
            
            // Декодируем код Base64 картинок и сохраняем как байтовый массив
            Decoder decoder = Base64.getMimeDecoder();
            byte[] decodedBytes = decoder.decode(binCode.get(i));
            
            try {
               BufferedImage img = ImageIO.read(new ByteArrayInputStream(decodedBytes));
               
               // Получаем расширения картинок
               String extImage = "";
               int extNum = 0;                              
               extNum = binImg.get(i).indexOf(".");
               extImage = binImg.get(i).substring(extNum + 1);
               
               // В зависимости от расширения сохраняем в соответствующий формат
               if(extImage.equals("jpg")) {         
                  File outputfile = new File(outputDir + binImg.get(i));
                  ImageIO.write(img, "jpg", outputfile);
               } else if(extImage.equals("png")) {
                  File outputfile = new File(outputDir + binImg.get(i));
                  ImageIO.write(img, "png", outputfile);               
               } else if(extImage.equals("gif")) {
                  File outputfile = new File(outputDir + binImg.get(i));
                  ImageIO.write(img, "gif", outputfile);
               }
            } catch (javax.imageio.IIOException e) {
               e.printStackTrace();
            }          
         }      
      }
      
      // Помещаем весь текстовый контент в одну переменную
      String text = description + "\n" + body;
      
      // Создаем панель WebView для отображения HTML контента
      WebView webView = new WebView();
      WebEngine webEngine = webView.getEngine();
      webEngine.loadContent(text);
      
      // Замена встроенных стилей CSS на собственные
      webEngine.setUserStyleSheetLocation("data:, @font-face {font-family: 'Open Sans', "
            + "sans-serif; src: local('Open Sans'), url(fonts/OpenSans-Regular.ttf);} "
            + "body {width: 90% !important; padding-left: 10px; font-size: 14pt; "
            + "font-family: 'Open Sans', sans-serif; line-height: 1.5;} "
            + "img {max-width: 90%; height: auto;}");

      BorderPane borderPane = new BorderPane();    
      borderPane.setCenter(webView);

      // Создание сцены и отображение окна
      Scene scene = new Scene(borderPane);
      stage.setScene(scene);
      stage.show();
      stage.setHeight(900);
      borderPane.setPrefHeight(5000);
      
      // Удаление временной папки при закрытии окна
      stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {           
         File tmpFls = new File(tempDir.toString());
         deleteDir(tmpFls);
     });
   }
   
   // Метод для удаления временной папки
   private static void deleteDir(File tmpFls) {
      File[] contents = tmpFls.listFiles();
      if (contents != null) {
          for (File f : contents) {
              if (! Files.isSymbolicLink(f.toPath())) {
                  deleteDir(f);
              }
          }
      }
      tmpFls.delete();             
    }
   
   // Метод для копирования файла
   public static void copyFile(File src, File dest) throws IOException {
     Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
   }
   
   // Метод для определения кодировки текстового файла
   private static String detectCharset(String filePath) {
     try (InputStream inputStream = new FileInputStream(filePath)) {
         byte[] bytes = new byte[4096];
         UniversalDetector detector = new UniversalDetector(null);
         int nread;
         while ((nread = inputStream.read(bytes)) > 0 && !detector.isDone()) {
             detector.handleData(bytes, 0, nread);
         }
         detector.dataEnd();
         return detector.getDetectedCharset();
     } catch (IOException e) {
         e.printStackTrace();
     }
     return null;
  }
   
   // Метод для чтения содержимого текстового файла, извлечения данных,
   // кодировки в UTF-8 и записи в переменную String
   private static String readText(String filePath, String charset) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
              new FileInputStream(filePath), charset))) {
          StringBuilder builder = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
              builder.append(line).append("\n");
          }
          return builder.toString();
      } catch (IOException e) {
          e.printStackTrace();
      }
      return null;
   }  
}
