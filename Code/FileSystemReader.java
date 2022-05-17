import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

import javax.print.StreamPrintService;


public class FileSystemReader{

    static String workingDir = "/";
    static String imgFile;
    static long dataStart;
    static int[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32;
    static int rootCluster;

    static int directoryLevel = 0;

    static BufferedInputStream fatBis;
    static BufferedInputStream bis;
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        imgFile = args[0];
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));

        setGlobals();
        //Should get to beginning of data section
        dataStart = rsvdSecCnt + (bytePerSec * secPerClus * Fatsz32 * numFats);
        bis.skip(dataStart);
        bis.mark(-1);
        
        Scanner cmdScanner = new Scanner(System.in);
        String input;
        String inputArray[];
        String task;
       
        while(true){
            System.out.print(workingDir + "] ");
            input = cmdScanner.nextLine();
            input.toLowerCase();
            inputArray = input.split(" ");
            task = inputArray[0];

            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    if (inputArray.length > 1){
                        ls(inputArray[1]);
                    } else {
                        ls(".");
                    }
                    break;
                case "stat":
                    if(inputArray.length == 1){
                        System.out.println("Error: Please provide an argument");  
                    } else {
                        stat(inputArray[1]);
                    }
                    break;
                case "size":
                    if(!cmdScanner.hasNext()){
                     System.out.println("Error: Please provide an argument");  
                     continue; 
                    }
                    size(cmdScanner.next());
                    break;
                case "cd":
                    if(!cmdScanner.hasNext()){
                        System.out.println("Error: Please provide an argument");  
                        continue; 
                    }
                    cd(cmdScanner.next());
                    break;
                case "read":
                    String[] inputs = new String[3];
                    boolean cont = false;
                    for(int i = 0; i < 3; i++){
                        if(cmdScanner.hasNext()){
                            inputs[i] = cmdScanner.next();
                        }else{
                            System.out.println("Error: Please provide the proper number of arguments");
                            cont = true;
                            break;
                        }
                    }
                    if(cont){
                        continue;
                    }
                    read(inputs[0], inputs[1], inputs[2]);
                    break;
                default:
                    System.out.println("Error: Improper command issued");
                    continue;
            }
        }
    }
    
    //
    //Returns the requested bytes as they are ordered in the stream
    //Does not change the pointer in the stream
    //byte array is the requested amount size
    //fat: true when initializing fat, false otherwise
    private static byte[] getBytes(int offset, int amount, boolean fat, BufferedInputStream streamToRead) throws IOException{

        byte[] scrap = new byte[amount];
        streamToRead.mark(offset + amount + 1);
        streamToRead.skip(offset);
        if(fat){
            int byteCounter = 0;
            int index = 0;
            byte[] fatEntry = new byte[4];
            while(byteCounter < amount){
                streamToRead.read(fatEntry, 0, 4);
                FAT[index++] = byteToInt(fatEntry);
                byteCounter += 4;
            }
        }else{
            streamToRead.read(scrap, 0, amount);
        }
        streamToRead.reset();
        return scrap;
    }

    private static void setFatBisPointer(int sector) throws IOException{

        fatBis.close();
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        fatBis.skip(sector * 32);
    }

    /**
    Does not change the pointer
     */
    private static void setGlobals() throws IOException{

        bytePerSec = byteToInt(getBytes(11, 2, false, bis));
        secPerClus = byteToInt(getBytes(13, 1, false, bis));
        rsvdSecCnt = byteToInt(getBytes(0x0E, 1, false, bis));
        numFats = byteToInt(getBytes(0x10, 1, false, bis));
        Fatsz32 = byteToInt(getBytes(0x24, 4, false, bis)); //sectors per fat
        rootCluster = byteToInt(getBytes(44, 4, false, bis));
        
        FAT = new int[Fatsz32 * bytePerSec]; 
        getBytes(rsvdSecCnt, (Fatsz32 * bytePerSec), true, bis);
        //System.out.println(FAT);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */
    private static int byteToInt (byte[] array){
        // String s = "";
        // for(int i = array.length-1; i >= 0; i--){
        //     s += String.format("%02X", array[i]);
        // }
        // return Integer.parseInt(s,16);

        int value = 0;
         for (int i = array.length - 1; i >= 0; i--){
             value = (value << 8) | (array[i] & 0xFF);
         }

         return value;
    }
    public static void info(){

        System.out.println("BPB_BytesPerSec is 0x" + String.format("%X", bytePerSec) + ", " + bytePerSec);
        System.out.println("BPB_SecPerClus is 0x" + String.format("%X", secPerClus)+ ", " + secPerClus);
        System.out.println("BPB_RsvdSecCnt is 0x" + String.format("%X", rsvdSecCnt) + ", " + rsvdSecCnt);
        System.out.println("BPB_NumFATS is 0x" + String.format("%X", numFats) + ", " + numFats);
        System.out.println("BPB_FATSz32 is 0x" + String.format("%X", Fatsz32) + ", " + Fatsz32);
        //System.out.println("FAT:\n" + Arrays.toString(FAT));
    }
    
    public static void ls(String dirName) throws IOException{

        int FATCluster = setDataEntryPointer (dirName, true, false);

        if (FATCluster == -1){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }

        parseDirectory("", FATCluster, true);
    }

    private static void stat(String fileNameDirName) throws IOException{
        
        if (setDataEntryPointer(fileNameDirName, false, false) == -1){
            System.out.println("Error: file/directory does not exist");
            return;
        }
        
        int size = byteToInt(getBytes(28, 4, false, bis));
        String attributes = attrToString(byteToInt(getBytes(11, 1, false, bis)));
        int startCluster = getStartCluster();
        

        System.out.println("Size is " + size);
        System.out.println("Attributes " + attributes);
        System.out.println("Next cluster number is " + String.format("%X", startCluster));
    }

    /**
        summary: returns the int value of the combined high bits and low bits of the target entry

        pointer before running: set to the target entry pointer
        pointer after running: must remain at the target entry pointer
        
        returns: the int value of the start cluster

     */
    private static int getStartCluster () throws IOException{
        byte[] firstHalf = getBytes(20, 2, false, bis);
        byte[] secondHalf = getBytes(26, 2, false, bis);
        
        byte[] full = new byte[4];
        for (int i = 0; i < 2; i++){
            full[i] = firstHalf[i];
        }
        for (int i = 0; i < 2; i++){
            full[i+2] = secondHalf[i];
        }
        return byteToInt(full);
    }
    
    private static String attrToString(int attr){
        String attributes = "";
        if((attr & 0x20) == 0x20){
            attributes += " ATTR_ARCHIVE"; 
        }
        if((attr & 0x10) == 0x10){
            attributes += " DIRECTORY";
        }
        if((attr & 0x8) == 0x8){
            attributes += " VOLUME_ID";
        }
        if((attr & 0x4) == 0x4){
            attributes += " SYSTEM";
        }
        if((attr & 0x2) == 0x2){
            attributes += " HIDDEN";
        }
        if((attr & 0x1) == 0x1){
            attributes += " READ_ONLY";
        }
        return attributes;        
    }
    public static void size (String fileName) throws IOException {

        if (setDataEntryPointer(fileName, false, false) == -1){
            System.out.println("Error: " + fileName + " is not a file");
            return;
        }
        int size = byteToInt(getBytes(28, 4, false, bis));
        System.out.println("Size of " + fileName + " is " + size + " bytes");
        return;
    }
    public static void cd (String dirName) throws IOException{
        if(setDataEntryPointer(dirName, false, true) == -1){
            System.out.println("Error: " + dirName + "is not a directory");
        }
    }
    public static void read(String fileName, String offset, String numBytes){

        

    }


        
    /**
     * Move through stream to desired point in data section
     * @param target intended endgoal
     * @return Cluster number where target directory begins
     */
    private static int setDataEntryPointer (String path, boolean ls, boolean cd) throws IOException{
        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(dataStart);
        boolean absolute = path.charAt(0) == '/';
        if(!absolute){
            //? What if pwd doesnt end with a /?, Not going to split properly
            path = (workingDir + (workingDir.charAt(workingDir.length() - 1) != '/' ? '/' : "") + path);
        }
        //Preproccess path string, handling periods
        //TODO When workingdir is in the root
        String[] levels = path.split("/");
        String processedPath = "/";
        for(int i = 0; i < levels.length; i++){
            String target = levels[i];
            if(target.equals('.')){
                continue;
            }
            if(target.equals("..")){
                //Meaning not in the root
                if(workingDir.length() > 1){
                    String[] backup = processedPath.split("/");
                    String newPath = "/";
                    for(int x = 0; x < backup.length - 1; x++){
                        newPath += (backup[x] + '/');
                    }
                    processedPath = newPath;
                }
            }else{
                processedPath += (target + '/');
            }
        }
        levels = processedPath.split("/");
        int nextFATIndex = rootCluster;
        for(int i = 0; i < levels.length; i++){
            int currentFATIndex = parseDirectory(levels[i], nextFATIndex, ls);
            if(currentFATIndex == -1){
                return currentFATIndex;
            }if(i == (levels.length - 1)){
                if(cd){
                    workingDir = processedPath.substring(0, processedPath.length() - 1);
                }
                return currentFATIndex;
            }
            int startCluster = getStartCluster();
            bis.skip((startCluster - currentFATIndex) * bytePerSec * secPerClus);
            //The number of the first cluster is also the index in teh fat to find the second cluster
            nextFATIndex = startCluster;

            //At this point bis pointer should be pointing at entry for the target directory
            //Need to read the entry to get the first cluster of the target directory



            //!Just Implemented
            /**
             * Start at root
             * call parsedirectory to find teh entry for the target subdirectory or file
             * once found, parsedirectory should return to setdataentrypointer
             * if this is not the end of the path provided by the user
             * (Meaning we intend to search further down the directory structure)
             * Then parse the entry and set teh pointer to teh beginning of the subdirectory's first cluster
             *      Works here, bc have access to the current cluster so we know how far to skip
             *      (first cluster of next - current cluster) to get to the beginning of the next cluster
             * At that point, the pointer will be pointing to the beginning of the current target directory in time for the
             * next iteration of the loop
             * When the next iterartion begins, the pointer is pointing at the beginning of teh directory
             * where we now want to search for the new target
             * However, if this is the overall target, the file or directory we are searching for,
             * then we want to leave the pointer pointing at the beginnign of the entry
             * this will allow the calling methods, like stat and size to access the information.
             */
            
            




            // if(startCluster == -1){
            //     return startCluster;
            // }
        }
        //Only reaches here if only "/" was passed in 
        return nextFATIndex;
    }
   
    //Can read the entry of the start directory either in the beginning of parse directory or after calling parsedirectory in setdataentrypointer
    
    /**
    summary: sets pointer to the entry of a target file or directory

    pointer before running: set to directory entry of directory to search
    pointer after running: set to directory or file entry of target
    
    target: file or directory to look for
    nextFATIndex: index of the FAT which points to the next cluster in the data to continue the search
    returns: cluster number in data section in which target was found; most recent FAT value
    */
    private static int parseDirectory(String target, int nextFATIndex, boolean ls) throws IOException{
        
        // because method requires pointer be set to the entry of the directory to search, semantics are ok for getStartCluster
        
        //Bc we are modeling ls -a, which starts with those two directories by default
        String accumulatedName = ". .. ";
        while(nextFATIndex != 0x0ffffff7){
            int byteCounter = 0;
                while(byteCounter < (bytePerSec * secPerClus)){
                    bis.mark(13);
                    int next = bis.read();
                    if(next == 65){
                        bis.skip(10);
                        int attr = bis.read();
                        if((attr & 0xF) == 0xF){
                            bis.skip(20);
                            byteCounter += 32;
                        }else{
                            bis.reset();
                        }
                    }
                    String name = getDataEntryName();
                    if (ls){
                        //reached last entry in directory so print
                        if (name.equals("0")){
                            System.out.println(accumulatedName.stripTrailing());
                        //entry must have been deleted but could have entries after this one
                        }else if(name.equals("229")){
                            bis.skip(32);
                            byteCounter += 32;
                        }else{
                            accumulatedName += name + " ";
                        }
                    }else{
                        if(target.equals(name)){
                            return nextFATIndex;
                        }else{
                            bis.skip(32);
                            byteCounter += 32;
                        }
                    }  
                }
            if(nextFATIndex >= 0x0ffffff8 && nextFATIndex <= 0x0fffffff){
                return -1;
            }
            setFatBisPointer(nextFATIndex);
            int nextCluster = byteToInt(getBytes(0, 4, false, fatBis));
            //FAT[nextFATIndex];
            bis.skip((Math.abs(nextCluster - nextFATIndex) * bytePerSec * secPerClus));
        }
        if(nextFATIndex == 0x0ffffff7){
            throw new IllegalStateException("Bad Cluster discovered");
        }else{
            throw new IllegalArgumentException("We got big problems: How did we get here?");
        }
    }
    //}

    /**
    Stream pointer must be set to the data entry
    */
    private static String getDataEntryName() throws IOException{

        byte[] nameBytes = getBytes(0, 11, false, bis);
        String name = "";
        if (nameBytes[0] == 0 || nameBytes[0] == 0xE5){
            return name + nameBytes[0];
        }
        for (int i = 0; i < nameBytes.length; i++){
            if (nameBytes[i] != 0){
                name += (char)nameBytes[i];
            }
        }
        return name;
    }
}