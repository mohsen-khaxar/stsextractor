package se.lnu.prosses.securityMonitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Utils {
	static public char[] readTextFile(String path){
		String res = "";
		String line = "";
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(path))) {
			while ((line = bufferedReader.readLine()) != null) {
				res += line + "\n";
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res.toCharArray();
	}
	
	static public void writeTextFile(String path, String content){
		try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(path))) {
			bufferedWriter.write(content);
			bufferedWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static public void renameFile(String path, String newName){
		File oldFile = new File(path);
		String newFilePath = path.substring(0, path.lastIndexOf(oldFile.getName())) + newName;
		oldFile.renameTo(new File(newFilePath));
	}
	
	static public <T> ArrayList<T> removeDuplicates(ArrayList<T> list){
		Set<T> hs = new HashSet<T>();
		hs.addAll(list);
		list.clear();
		list.addAll(hs);
		return list;
	}
	
	static public <T> boolean areEqual(List<T> list1, List<T> list2){
		boolean res = true;
		for (T t : list1) {
			if(!list2.contains(t)){
				res = false;
				break;
			}
		}
		for (T t : list2) {
			if(!list1.contains(t)){
				res = false;
				break;
			}
		}
		return res;
	}
}
