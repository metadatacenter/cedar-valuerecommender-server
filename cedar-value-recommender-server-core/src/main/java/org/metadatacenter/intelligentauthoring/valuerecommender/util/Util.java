package org.metadatacenter.intelligentauthoring.valuerecommender.util;

import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {

  /**
   * Index a specific json content in Elastic Search
   */
  public static void indexJson(Client client, String indexName, String typeName, String json) {
    IndexResponse response = client.prepareIndex(indexName, typeName)
        .setSource(json)
        .get();
    System.out.println(response.toString());
  }

  /**
   * Index all files in a folder in ElasticSearch.
   *
   * Example: call to index GEO instances:
   * Util.indexAllFilesInFolder(client, "cedar", "template_instances", "data/sample-data/GEOFlatSamples");
   */
  public static void indexAllFilesInFolder(Client client, String indexName, String typeName, String folderPath)
      throws IOException {
    File folder = new File(folderPath);
    File[] listOfFiles = folder.listFiles();

    for (int i = 0; i < listOfFiles.length; i++) {
      File file = listOfFiles[i];
      if (file.isFile()) {
        String fileContent = readFile(file.getPath(), StandardCharsets.UTF_8);
        indexJson(client, indexName, typeName, fileContent);
      }
    }
  }

  public static String readFile(String path, Charset encoding)
      throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }
}
