import org.apache.commons.fileupload.FileItem;
class Uploads {
  String a(FileItem item) {
    return item.getName();
  }
}
