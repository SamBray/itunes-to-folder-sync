import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.regex.*;
import java.nio.file.NoSuchFileException;

public class SyncCheckedSongs {
	public final static String DEFAULT_LIBRARY_PATH = "/iTunes Music";
	
	public static void main(String[] args) {
		Scanner keyboard = new Scanner(System.in);
		String iTunesPath = null, syncPath = null, iTunesMusicPath = null;
		if(args.length != 2 && args.length != 3) {
			System.out.println("Please add the following arguments: \n\t1. Your iTunes folder location"
					+ "\n\t2. The location where you want your songs synced to\n\t3. (optional) Your iTunes"
					+ " music folder location if it differs from the default 'iTunes/iTunes Music' directory");
			return;
		}
		iTunesPath = args[0];
		syncPath = args[1];
		//check that paths exist and are directories
		if(!Files.exists(Paths.get(syncPath)) && !Files.isDirectory(Paths.get(syncPath))) {
			System.out.println("Error: " + syncPath + " does not exist or is not a directory.");
			return;
		}
		
		if(!Files.exists(Paths.get(iTunesPath)) && !Files.isDirectory(Paths.get(iTunesPath))) {
			System.out.println("Error: " + iTunesPath + " does not exist or is not a directory.");
			return;
		}
		//replace \ with / if needed
		iTunesPath = iTunesPath.replace("\\","/");
		syncPath = syncPath.replace("\\", "/");
		//add terminal / if it doesn't already exist
		if(!iTunesPath.endsWith("/"))
			iTunesPath = iTunesPath + "/";
		if(!syncPath.endsWith("/"))
			syncPath = syncPath + "/";
		//set up iTunesMusicPath
		if(args.length == 3) {
			iTunesMusicPath = args[2];
			if(!Files.exists(Paths.get(iTunesMusicPath)) && !Files.isDirectory(Paths.get(iTunesMusicPath))) {
				System.out.println("Error: " + iTunesMusicPath + " does not exist or is not a directory.");
				return;
			}
			iTunesMusicPath = iTunesMusicPath.replace("\\","/");
			if(!iTunesMusicPath.endsWith("/"))
				iTunesMusicPath = iTunesMusicPath + "/";
		}		
		else
			iTunesMusicPath = iTunesPath + "iTunes Music/";
		
		if(!syncPath.toLowerCase().endsWith("music/")) {
			System.out.println("WARNING: This will delete everything in " + syncPath + " that is not part of your iTunes library."
					+ " Are you sure you want to continue? (yes/no)");
			String input = keyboard.nextLine();
			while(!input.toLowerCase().equals("yes")) {
				if(input.toLowerCase().equals("no")) {
					return;
				}else {
					System.out.println("Please enter 'yes' or 'no'");
					input = keyboard.nextLine();
				}
			}
			System.out.println("Are you sure you're sure? (yes/no)");
			input = keyboard.nextLine();
			while(!input.toLowerCase().equals("yes")) {
				if(input.toLowerCase().equals("no")) {
					return;
				}else {
					System.out.println("Please enter 'yes' or 'no'");
					input = keyboard.nextLine();
				}
			}
		}
		
		keyboard.close();
		
		//System.out.println(iTunesPath + "\n" + syncPath + "\n" + iTunesMusicPath);
		
		
		HashMap<String, Object> library = null;
		try {
			library = readItunesLibrary(iTunesPath, iTunesMusicPath);
		}catch(IOException e) {}
		
		if(library != null)
			syncFiles(library, iTunesMusicPath, syncPath);
		
		System.out.println("Operation complete.");
		
		/*
		Set<String> keySet = library.keySet();
		Iterator<String> keys = keySet.iterator();
		System.out.println();
		while(keys.hasNext()) {
			System.out.println(keys.next());
		}
		*/
	}
	
	public static void syncFiles(HashMap<String, Object> library, String fromPath, String toPath)
	{
		try {
			Path currentDirPath = Paths.get(toPath);
			if(Files.exists(currentDirPath)) {
				//read contents of directory
				DirectoryStream<Path> fileContents = Files.newDirectoryStream(currentDirPath);
				for(Path file: fileContents) {
					//System.out.println(file.toString());
					if(Files.exists(file)) {
						String currentFile = file.getFileName().toString();
						//System.out.println(currentFile);
						if(Files.isDirectory(file)) {
							currentFile = currentFile + "/";
							String newToPath = toPath + currentFile;
							String newFromPath = fromPath + currentFile;
							@SuppressWarnings("unchecked")
							HashMap<String, Object> currentDirMap = (HashMap<String, Object>) library.get(currentFile);
							if(currentDirMap == null) {
								//this directory does not exist in the library - delete it
								Files.walkFileTree(Paths.get(newToPath), new SimpleFileVisitor<Path>() {
									   @Override
									   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
									       Files.delete(file);
									       return FileVisitResult.CONTINUE;
									   }
	
									   @Override
									   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
									       Files.delete(dir);
									       return FileVisitResult.CONTINUE;
									   }
								});
							}else {
								//this directory is in the library - recurse on it
								syncFiles(currentDirMap, newFromPath, newToPath);
								//when finished, remove it from the library
								library.remove(currentFile);
							}
						}else {
							//This is a file
							if(library.containsKey(currentFile)) {
								//file is in the library - check date modified to see if it needs to be replaced
								try{
									FileTime lastModified = Files.getLastModifiedTime(file);
									Path iTunesFile = Paths.get(fromPath + currentFile);
									FileTime iTunesLastModified = Files.getLastModifiedTime(iTunesFile);
									if(iTunesLastModified.compareTo(lastModified) > 0) {
											Files.copy(iTunesFile, file, StandardCopyOption.REPLACE_EXISTING);
									}
								}catch(NoSuchFileException f){System.out.println("Sync Error: " + f);}
								//when finished, remove it from the library
								library.remove(currentFile);
							}else {
								//file is not in library - delete it
								//System.out.println("Deleting file");
								Files.delete(file);
							}
						}
					}
				}
			}
			//at this point we are left with only things that did not already exist in the library
			Iterator<Map.Entry<String,Object>> itemsToCopy = library.entrySet().iterator();
			while(itemsToCopy.hasNext()) {
				Map.Entry<String, Object> currentEntry = itemsToCopy.next();
				String newToPath = toPath + currentEntry.getKey();
				String newFromPath = fromPath + currentEntry.getKey();
				if(newToPath.endsWith("/")) {
					//this is a directory - create it in destination directory
					Files.createDirectory(Paths.get(newToPath));
					//recurse
					@SuppressWarnings("unchecked")
					HashMap<String, Object> subLibrary = (HashMap<String, Object>) currentEntry.getValue();
					syncFiles(subLibrary, newFromPath, newToPath);
				}else {
					//this is a file - copy it
					try{
						//System.out.println(Files.exists(Paths.get(newToPath)));
						Files.copy(Paths.get(newFromPath), Paths.get(newToPath));
						//System.out.println("Copying " + newToPath);
					}catch(NoSuchFileException f){System.out.println("Sync Error: " + f);}
				}
			}
		}catch(Exception e) {System.out.println("Sync Error: " + e);}
	}
	
	public static HashMap<String, Object> readItunesLibrary(String itunesPath, String libraryPath) throws IOException
	{
		HashMap<String, Object> library = new HashMap<>();
		
		//attempt to read the file
		File file = new File(itunesPath + "iTunes Music Library.xml");
		if(!file.exists() || file.isDirectory()) {
			System.out.println("Unable to open your iTunes Library database.  Make sure the path to your iTunes folder is correct.");
			return null;
		}
		BufferedReader fileLines = null;
		try {
			fileLines = new BufferedReader(new FileReader(file));
			String line = fileLines.readLine();
			int dictLevel = 0;
			boolean skip = false;
			boolean firstFile = true;
			Pattern keyPattern = Pattern.compile(".*<key>(.*)</key>.*");
			Pattern locationPattern = Pattern.compile(".*(\\w:.*)<.*");
			//Pattern subLocationPattern = Pattern.compile(libraryPath + "(.*)");
			Pattern subLocationPattern = null;
			Pattern pathPattern = Pattern.compile("([^/]+/?)");
			while(line != null) {
				if(line.contains("<dict>")) {
					dictLevel++;
					skip = false;
				}else if(line.contains("</dict>")) {
					dictLevel--;
				}
				else {
					if(dictLevel == 3) {
						Matcher m = keyPattern.matcher(line);
						String key;
						if(m.matches())
							key = m.group(1);
						else
							key = "";
						if(key.equals("Disabled") && line.contains("true")){
							//the song is unchecked
							skip = true;
						}else if(key.equals("Location") && !skip) {
							//if the song is checked get its location and add it to the library hashmap
							m = locationPattern.matcher(line);;
							if(!m.matches()) {
								System.err.println("Error reading file name");
								line = fileLines.readLine();
								continue;
							}else {
								String location = m.group(1);
								//replace xml escape characters in location string
								location = location.replace("&#34;", "\"");
								location = location.replace("&#38;", "&");
								location = location.replace("&#39;", "'");
								location = location.replace("&#60;", "<");
								location = location.replace("&#62;", ">");
								//convert uri to path string
								try {
									//convert and add the drive back
									location = (new URI(location).getPath());
								}catch(URISyntaxException u) {
									line = fileLines.readLine();
									continue;
								}
								if(firstFile){
									//set up the subLocationPattern
									//find the name of the directory storing the library
									Pattern libraryFolderPattern = Pattern.compile(".*/([^/]*/)$");
									m = libraryFolderPattern.matcher(libraryPath);
									m.matches();
									String libraryFolder = m.group(1);
									//System.out.println("library folder is " + libraryFolder);
									//find the path of the library folder relative to the full path in iTunes
									Pattern findLibraryFolderInLocation = Pattern.compile("(.*" + libraryFolder + ").*");
									//System.out.println(location);
									m = findLibraryFolderInLocation.matcher(location);
									String libraryPathCandidate = "";
									boolean found = false;
									while(m.matches() && !found){
										libraryPathCandidate = m.group(1);
										//System.out.println(libraryPathCandidate);
										if(libraryPath.contains(libraryPathCandidate))
											found = true;
										else {
											m = findLibraryFolderInLocation.matcher(libraryPathCandidate.substring(0, libraryPathCandidate.length() - 1));
										}
									}
									if(found == false){
										System.out.println("Error: library location does not match iTunes database");
										return null;
									}
									
									subLocationPattern = Pattern.compile(libraryPathCandidate + "(.*)");
									firstFile = false;
								}
								//System.out.println(location);
								//get the location of the file relative to the library folder
								m = subLocationPattern.matcher(location);
								if(!m.matches()) {
									System.out.println("Error: library location does not match iTunes database");
									return null;
								}else {
									String subLocation = m.group(1);
									//System.out.println(subLocation);
									//add this file to the hashmap
									m = pathPattern.matcher(subLocation);
									HashMap<String, Object> currentMap = library;
									
									while(m.find()){
										String currentLevel = m.group(1);
										//System.out.print(currentLevel);
										if (currentLevel.endsWith("/")) {
											//System.out.println(" :: is a directory.");
											//this is a directory
											//currentLevel = "dir:" + currentLevel;
											@SuppressWarnings("unchecked")
											HashMap<String, Object> nextMap = (HashMap<String,Object>) currentMap.get(currentLevel);
											if(nextMap == null) {
												//System.out.println("\tCreating a new mapping");
												//the mapping does not exist - create it
												nextMap = new HashMap<>();
												currentMap.put(currentLevel, nextMap);
											}
											currentMap = nextMap;
										}
										else {
											//System.out.println(" :: is a file.");
											//this is the actual file in question
											//currentLevel = "file:" + currentLevel;
											//just put null to indicate we've reached the end
											currentMap.put(currentLevel, null);
										}
										
									}
									
								}
							}
						}
					}
				}
				line = fileLines.readLine();
			}
		}catch(IOException e) {
			System.out.println("Error reading iTunes library file.  Make sure the path to your iTunes folder is correct.");
			return null;
		}finally {
			if(fileLines != null)
				fileLines.close();
		}
		return library;
	}

}
