package com.tmax.ck.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translate.TranslateOption;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class Main {
//	static List<List<String>> keySheet = new ArrayList<List<String>>();
	static Map<String, List<List<String>>> keySheetMap = new HashMap<String, List<List<String>>>();
	static Integer sequence = new Integer(0);
	static Map<String, JsonObject> schemaMap = new HashMap<String, JsonObject>();
	static Map<String, Map<String, Object>> yamlMap = new HashMap<String, Map<String, Object>>();
	static Gson gsonObj = new Gson();
	static ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
	static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 100, 0, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<Runnable>());

	// configuration
	static boolean autoTranslation = false;

	public static void main(String args[]) {

		System.out.println("Start");
		try {
			Translate translate = null;
			if (autoTranslation) {
				translate = TranslateOptions.newBuilder().build().getService();
			}

			String rootDir = "C:\\schema\\";
			String outputDir = rootDir + System.currentTimeMillis() + "\\";

			File rootDirFile = new File(rootDir);
			if (!rootDirFile.isDirectory()) {
				System.out.println(rootDir + " is not directory");
				return;
			}

			File outputDirFile = new File(outputDir);
			if (!outputDirFile.exists()) {
				outputDirFile.mkdir();
			}

			File[] jsonFiles = rootDirFile.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getAbsolutePath().endsWith(".json"))
						return true;
					else
						return false;
				}
			});

			File[] yamlFiles = rootDirFile.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getAbsolutePath().endsWith(".yaml"))
						return true;
					else
						return false;
				}
			});

			for (File jsonFile : jsonFiles) {
				FileReader fr = new FileReader(jsonFile);
				schemaMap.put(jsonFile.getName(), gsonObj.fromJson(fr, JsonObject.class));
			}

			for (File yamlFile : yamlFiles) {
				yamlMap.put(yamlFile.getName(), mapper.readValue(yamlFile, Map.class));
			}

			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);
				List<List<String>> keySheet = new ArrayList<List<String>>();
				convertDescriptionToCode(keySheet, schema, schemaKey);
				keySheetMap.put(schemaKey, keySheet);
			}

			for (String yamlKey : yamlMap.keySet()) {
				Map<String, Object> yaml = yamlMap.get(yamlKey);
				List<List<String>> keySheet = new ArrayList<List<String>>();
				convertCrdDescriptionToCode(keySheet, yaml, yamlKey);
				keySheetMap.put(yamlKey, keySheet);
			}

			for (String schemaKey : schemaMap.keySet()) {
				JsonObject schema = schemaMap.get(schemaKey);

				FileWriter fw = new FileWriter(new File(outputDir + schemaKey));
				PrintWriter pw = new PrintWriter(fw);
				pw.print(schema.toString());
				pw.close();
			}

			for (String yamlKey : yamlMap.keySet()) {
				mapper.writeValue(new File(outputDir + yamlKey), yamlMap.get(yamlKey));
			}

			// batch ó���� ���ؼ� �ۼ��� �ڵ�. ���� payload�� �ʹ� ũ�� 400 ������ �߻��ϴ� ������ �־ �ּ�ó��
//			List<Translation> translatedList = translate.translate(originalList,
//					TranslateOption.sourceLanguage("en").targetLanguage("ko"));

			HSSFWorkbook workbook = new HSSFWorkbook();

			for (String sheetKey : keySheetMap.keySet()) {
				int i = 0;
				HSSFSheet sheet = workbook.createSheet(sheetKey);

				for (List<String> pair : keySheetMap.get(sheetKey)) {
					HSSFRow row = sheet.createRow(i);

					HSSFCell cell1 = row.createCell(0);
					HSSFCell cell2 = row.createCell(1);
					HSSFCell cell3 = row.createCell(2);
					System.out.println(pair.get(0));
					cell1.setCellValue(pair.get(0));
					cell2.setCellValue(pair.get(1));
					// cell3.setCellValue(translatedList.get(i).getTranslatedText());

					if (autoTranslation) {
						String translated = translate
								.translate(pair.get(1), TranslateOption.sourceLanguage("en").targetLanguage("ko"))
								.getTranslatedText();
						cell3.setCellValue(translated);
						System.out.println(translated);
					}
					i++;
				}

			}

			workbook.write(new File(outputDir + "output.xls"));
			workbook.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("End");
	}

	static void convertDescriptionToCode(List<List<String>> keySheet, JsonObject schema, String path) {
		for (String key : schema.keySet()) {
			if (schema.get(key).isJsonObject()) {
				convertDescriptionToCode(keySheet, (JsonObject) schema.get(key), path + "." + key);
			} else if (key.equals("description")) {
				String original = schema.get(key).getAsString();
//				String code = "%" + path + String.valueOf(sequence++) + "";
				String code = "%" + path;
				List<String> codePair = new ArrayList<String>();
				codePair.add(code);
				codePair.add(original);
				keySheet.add(codePair);
				schema.addProperty(key, code);
			}
		}
	}

	static void convertCrdDescriptionToCode(List<List<String>> keySheet, Map<String, Object> yaml, String path) {
		for (String key : yaml.keySet()) {
			if (yaml.get(key) instanceof Map<?, ?>) {
				convertCrdDescriptionToCode(keySheet, (Map<String, Object>) yaml.get(key), path + "." + key);
			} else if (key.equals("description")) {
				System.out.println(yaml.get(key));
				String original = (String) yaml.get(key);
//				String code = "%" + path + String.valueOf(sequence++) + "";
				String code = "%" + path;
				List<String> codePair = new ArrayList<String>();
				codePair.add(code);
				codePair.add(original);
				keySheet.add(codePair);
				yaml.replace(key, code);
			}
		}
	}
}
