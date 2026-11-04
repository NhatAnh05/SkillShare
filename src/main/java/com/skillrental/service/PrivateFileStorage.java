package com.skillrental.service;
import org.springframework.stereotype.Service;import org.springframework.web.multipart.MultipartFile;import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.io.*;import java.util.zip.*;import java.util.concurrent.TimeUnit;
@Service
public class PrivateFileStorage {
 @Value("${skillshare.delivery.storage-dir:./private-deliveries}") private String directory;
 @Value("${skillshare.files.clamav-required:false}") private boolean scannerRequired;
 @Value("${skillshare.files.scanner-command:clamdscan}") private String scannerCommand;
 private static final Set<String> TYPES=Set.of("txt","md","csv","json","pdf","png","jpg","jpeg","webp","docx","xlsx","pptx","zip");
 public Stored store(MultipartFile upload){return store(upload,directory);}
 public Stored store(MultipartFile upload,String storageDirectory){
  try {
   String name=Optional.ofNullable(upload.getOriginalFilename()).orElse("file").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","_");
   if(name.isBlank()||name.length()>255)throw new IllegalArgumentException("Tên tệp không hợp lệ.");
   String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
   if(!TYPES.contains(ext))throw new IllegalArgumentException("Loại tệp chưa được hỗ trợ. Dùng PDF, tài liệu Office không macro, ảnh, văn bản hoặc ZIP.");
   byte[] data=upload.getBytes();if(data.length==0||data.length>10*1024*1024)throw new IllegalArgumentException("Mỗi tệp phải từ 1 byte đến 10 MB.");
   inspect(data,ext);
   Path root=Path.of(storageDirectory).toAbsolutePath().normalize();Files.createDirectories(root);String key=UUID.randomUUID().toString();Path path=root.resolve(key);
   Files.write(path,data,StandardOpenOption.CREATE_NEW);
   try {
    if(scannerRequired){Process process=new ProcessBuilder(scannerCommand,"--no-summary",path.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
      if(!process.waitFor(15,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalArgumentException("Kiểm tra tệp quá thời gian; chưa lưu tài liệu.");}
      if(process.exitValue()!=0)throw new IllegalArgumentException("Tệp bị từ chối hoặc dịch vụ kiểm tra mã độc không sẵn sàng.");}
    if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{Files.deleteIfExists(path);}catch(IOException ignored){}}});
    return new Stored(name,key,data.length,"application/octet-stream");
   }catch(Exception ex){Files.deleteIfExists(path);if(ex instanceof InterruptedException)Thread.currentThread().interrupt();throw ex;}
  }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Không thể lưu hoặc kiểm tra tệp. Hãy thử lại; dữ liệu nhập được giữ nguyên.",ex);}
 }
 private void inspect(byte[] data,String ext)throws IOException {
  if(data.length>1&&data[0]=='M'&&data[1]=='Z')throw new IllegalArgumentException("Không nhận tệp thực thi.");
  if(Set.of("txt","md","csv","json").contains(ext)){for(byte b:data)if(b==0)throw new IllegalArgumentException("Nội dung không phải văn bản.");return;}
  if(ext.equals("pdf")){String content=new String(data,StandardCharsets.ISO_8859_1);if(!content.startsWith("%PDF-")||content.contains("/JavaScript")||content.contains("/Launch")||content.contains("/EmbeddedFile"))throw new IllegalArgumentException("PDF không hợp lệ hoặc có nội dung chủ động.");return;}
  if(ext.equals("png")){if(data.length<8||data[0]!=(byte)137||data[1]!='P'||data[2]!='N'||data[3]!='G')throw new IllegalArgumentException("Ảnh PNG không hợp lệ.");return;}
  if(ext.equals("jpg")||ext.equals("jpeg")){if(data.length<3||data[0]!=(byte)255||data[1]!=(byte)216||data[2]!=(byte)255)throw new IllegalArgumentException("Ảnh JPEG không hợp lệ.");return;}
  if(ext.equals("webp")){if(data.length<12||!new String(data,0,4,StandardCharsets.US_ASCII).equals("RIFF")||!new String(data,8,4,StandardCharsets.US_ASCII).equals("WEBP"))throw new IllegalArgumentException("Ảnh WebP không hợp lệ.");return;}
  if(data.length<4||data[0]!='P'||data[1]!='K')throw new IllegalArgumentException("ZIP/Office không hợp lệ.");
  long total=0;int count=0;boolean office=false;try(var zip=new ZipInputStream(new ByteArrayInputStream(data))){ZipEntry e;byte[] buf=new byte[8192];while((e=zip.getNextEntry())!=null){
   String n=e.getName().toLowerCase(Locale.ROOT).replace('\\','/');
   if(++count>500||n.startsWith("/")||Arrays.asList(n.split("/")).contains("..")||n.matches(".*\\.(exe|dll|msi|bat|cmd|ps1|vbs|scr|jar|sh)$")||n.contains("vbaproject"))throw new IllegalArgumentException("Nội dung ZIP/Office không an toàn.");
   if(n.equals("[content_types].xml"))office=true;int read;while((read=zip.read(buf))!=-1){total+=read;if(total>50L*1024*1024)throw new IllegalArgumentException("ZIP giải nén vượt 50 MB.");}
  }}if(count==0||(!ext.equals("zip")&&!office))throw new IllegalArgumentException("ZIP/Office không hợp lệ.");
 }
 public org.springframework.core.io.Resource resource(String key){try{UUID.fromString(key);}catch(Exception ex){throw new IllegalArgumentException("Mã tệp không hợp lệ.");}Path root=Path.of(directory).toAbsolutePath().normalize();Path path=root.resolve(key);if(!path.getParent().equals(root)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Tệp không còn khả dụng.");return new org.springframework.core.io.FileSystemResource(path);}
 public record Stored(String name,String key,long size,String type){}
}
