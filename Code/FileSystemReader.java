import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Scanner;



public class FileSystemReader{

    private static String workingDir = "/";
    static String imgFile;
    static long dataStart;
    static int[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32; //sectors per fat
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
        dataStart = (rsvdSecCnt * bytePerSec) + (bytePerSec * secPerClus * Fatsz32 * numFats);
        bis.skip(dataStart);
        bis.mark(-1);
        
        Scanner cmdScanner = new Scanner(System.in);
        String input;
        String inputArray[];
        String task;
       
        while(true){
            System.out.print(workingDir + "] ");
            input = cmdScanner.nextLine();
            input = input.toLowerCase();
            inputArray = input.split(" ");
            task = inputArray[0];

            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    if (inputArray.length == 2 && inputArray[1] != "."){
                        ls(inputArray[1]);
                    } else {
                        ls(workingDir);
                    }
                    break;
                case "stat":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                    } else {
                        stat(inputArray[1]);
                    }
                    break;
                case "size":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        size(inputArray[1]);
                    }
                    break;
                case "cd":
                    if(inputArray.length != 2){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        cd(inputArray[1]);
                    }
                    break;    
                case "read":
                    if(inputArray.length != 4){
                        System.out.println("Error: Please provide correct number of arguments");  
                        continue; 
                    } else {
                        read(inputArray[1], inputArray[2], inputArray[3]);
                    }
                    break; 
                default:
                    System.out.println("Error: Improper command issued");
                    continue;
            }
        }
    }
    
    /**
     * pointer is changed to the last byte of the 4th byte in address
     * @param address
     * @return
     * @throws IOException
     */
    private static int getIntFromFat(int address) throws IOException{

        fatBis.close();
        fatBis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        fatBis.skip(rsvdSecCnt * bytePerSec);

        byte[] value = new byte[4];
        fatBis.skip((address) * 4);
        fatBis.read(value, 0, 4);
        return byteToInt(value);
    }

    //Returns the requested bytes as they are ordered in the stream
    //Does not change the pointer in the stream
    //byte array is the requested amount size
    //fat: true when initializing fat, false otherwise
    private static byte[] getBytes(int offset, int amount, boolean fat) throws IOException{

        byte[] scrap = new byte[amount];
    
        if(fat){
            fatBis.read(scrap, 0, amount);
        }else{
            bis.mark(offset + amount + 1);
            bis.skip(offset);
            bis.read(scrap, 0, amount);
            bis.reset();
        }
        return scrap;
    }

    /**
    Does not change the pointer
     */
    private static void setGlobals() throws IOException{

        bytePerSec = byteToInt(getBytes(11, 2, false));
        secPerClus = byteToInt(getBytes(13, 1, false));
        rsvdSecCnt = byteToInt(getBytes(0x0E, 1, false));
        numFats = byteToInt(getBytes(0x10, 1, false));
        Fatsz32 = byteToInt(getBytes(0x24, 4, false)); //sectors per fat
        rootCluster = byteToInt(getBytes(44, 4, false));

        fatBis.skip(rsvdSecCnt * bytePerSec);
    }

    /**
     * Converts a binary array in little endian to an int
     * @param array little endian array
     * @param end the last index in the array with relevant content; important if the array is larger than needed
     * @return
     */
    private static int byteToInt (byte[] array){

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
    }
    
    public static void ls(String dirName) throws IOException{
        int FATCluster = setDataEntryPointer(dirName, false, false);
        int attr = byteToInt(getBytes(11, 1, false));
        if (FATCluster == -1 || (attr & 32) == 32){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }
        int cluster = (processPath(dirName).equals("/") ? 2 : getStartCluster());
        setPointerCluster(cluster);
        parseDirectory("", cluster, true);
    }
    private static void stat(String fileNameDirName) throws IOException{
        
        if (setDataEntryPointer(fileNameDirName, false, false) == -1){
            System.out.println("Error: file/directory does not exist");
            return;
        }
        int size = byteToInt(getBytes(28, 4, false));
        String attributes = attrToString(byteToInt(getBytes(11, 1, false)));
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
        byte[] firstHalf = getBytes(20, 2, false);
        byte[] secondHalf = getBytes(26, 2, false);
        byte[] full = new byte[4];
        full[0] = firstHalf[1];
        full[1] = firstHalf[0];
        full[2] = secondHalf[1];
        full[3] = secondHalf[0];
        int value = 0;
        for (int i = 0; i < full.length; i++){
            value = (value << 8) | (full[i] & 0xFF);
        }
        return value;
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
        int size = byteToInt(getBytes(28, 4, false));
        System.out.println("Size of " + fileName + " is " + size + " bytes");
        return;
    }
    
    public static void cd (String dirName) throws IOException{
        if(setDataEntryPointer(dirName, false, true) == -1){
            System.out.println("Error: " + dirName + " is not a directory");
            return;
        }
        String newWorking = processPath(dirName);
        workingDir = newWorking;
    }
    
    public static void read(String fileName, String offset, String numBytes) throws IOException{
        int off = 0;
        int numberBytes = 0;
        try{
            off = Integer.parseInt(offset);
            numberBytes = Integer.parseInt(numBytes);
        }catch(NumberFormatException e){
            System.out.println("Error: Please provide a numerical offset and number of bytes to be read" );
            return;
        }
        if(off < 0){
            System.out.println("Error: OFFSET must be a positive value");
            return;
        }
        if(numberBytes <= 0){
            System.out.println("Error: NUM_BYTES must be a greater than zero");
            return;
        }
        
        int cluster = setDataEntryPointer(fileName, false, false);
        int attr = byteToInt(getBytes(11, 1, false));
        if(cluster == -1 || (attr & 0x8) == 0x8){
            System.out.println("Error: " + fileName + " is not a file");
            return;
        }
        int size = byteToInt(getBytes(28, 4, false));
        System.out.println("File size: " + size);
        if(size <= (off + numberBytes)){
            System.out.println("Error: attempt to read data outside of file bounds");
            return;
        }
        int startCluster = getStartCluster();
        setPointerCluster(startCluster);
        byte[] readInfo = getBytes(off, numberBytes, false);
        String result = "";
        for(int i = 0; i < readInfo.length; i++){
            result += (char)readInfo[i];
        }
        System.out.println(result);
        return;
    }


    /**
     * Returns an absolute path without periods
     * @param path
     * @return
     */
    private static String processPath (String path){
        boolean absolute = path.charAt(0) == '/';
        if(!absolute){
            //? What if pwd doesnt end with a /?, Not going to split properly
            path = (workingDir + (workingDir.charAt(workingDir.length() - 1) != '/' ? '/' : "") + path);
        }
        String[] levels = path.split("/");
        String processedPath = "/";
        for(int i = 1; i < levels.length; i++){ //starts at one because all paths start with '/', and therefore all levels[] have "" at the beginning
            String target = levels[i];
            if(target.equals(".")){
                continue;
            }
            if(target.equals("..")){
                //Meaning not in the root
                if(processedPath.length() > 1){
                    String[] backup = processedPath.split("/");
                    String newPath = "/";
                    for(int x = 1; x < backup.length - 1; x++){
                        newPath += (backup[x] + '/');
                    }
                    processedPath = newPath;
                }
            }else{
                processedPath += (target + '/');
            }
        }
        if(processedPath.length() != 1){
            return processedPath.substring(0, processedPath.length() - 1);
        }else{
            return processedPath;
        }
        
    }

        
    /**
     * Move through stream to desired point in data section
     * @param target intended endgoal
     * @return Cluster number where target directory begins
     */
    private static int setDataEntryPointer (String path, boolean ls, boolean cd) throws IOException{
        setPointerCluster(rootCluster);        
        String processedPath = processPath(path);
        String [] levels = processedPath.split("/");
        int nextFATIndex = rootCluster;
        for(int i = 1; i < levels.length; i++){ //starts at one because all paths start with '/', and therefore all levels[] have "" at the beginning
            int currentFATIndex = parseDirectory(levels[i], nextFATIndex, ls);
            if(currentFATIndex == -1){
                return currentFATIndex;
            }
            if(i < levels.length - 1){
                int startCluster = getStartCluster();    
                setPointerCluster(startCluster);
                nextFATIndex = startCluster;
            }
        }
        return nextFATIndex;
    }
       
    /**
    summary: sets pointer to the entry of a target file or directory

    pointer before running: set to directory entry of directory to search
    pointer after running: set to directory or file entry of target
    
    target: file or directory to look for
    nextFATIndex: index of the FAT which points to the next cluster in the data to continue the search
    returns: cluster number in data section in which target was found; most recent FAT value
    */
    private static int parseDirectory(String target, int nextFATIndex, boolean ls) throws IOException{
        target = target.toUpperCase();
        String accumulatedName = "";
        boolean lsBreak = false;
        while(nextFATIndex != 0x0ffffff7){
            int byteCounter = 0;
                while(byteCounter < (bytePerSec * secPerClus)){
                    int firstByte = byteToInt(getBytes(0, 1, false));
                    int volID = byteToInt(getBytes(11, 1, false));
                    if((volID & 0x8) == 0x8){
                        bis.skip(32);
                        byteCounter+= 32;
                        continue;
                    }
                    if(firstByte == 65){ //checks if entry starts with A
                        
                        byte[] attributeByte = getBytes(11, 1, false);
                        if ((attributeByte[0] & 0xF) == 0xF){
                            bis.skip(32);
                            byteCounter+= 32;
                            continue;
                        }
                    }
                    String name = getDataEntryName();
                        if(target.equals(name)){
                            return nextFATIndex;
                        }
                        if(ls){
                            if(!name.equals("0")){
                                accumulatedName += name + " ";
                            }else{
                                lsBreak = true;
                                break;
                            }
                        }
                        bis.skip(32);
                        byteCounter += 32;
                }
            if(nextFATIndex >= 0x0ffffff8 && nextFATIndex <= 0x0fffffff){
                break;
            }
            if(lsBreak){
                break;
            }
            int nextCluster = getIntFromFat(nextFATIndex);
            int newAddress = setPointerCluster(nextCluster);
            nextFATIndex = nextCluster;
        }
        if(ls){
            System.out.println(accumulatedName.stripTrailing());
            //Don't really need it but have to return something
            return nextFATIndex;
        }
        if(nextFATIndex == 0x0ffffff7){
            throw new IllegalStateException("Bad Cluster discovered");
        }
        return -1;
    }

    /**
     * 
     * @param cluster cluster in the data section
     * @return the byte from the start of the image
     */
    private static int setPointerCluster (int cluster) throws IOException{
        int clusterInBytes = ((cluster - 2) * bytePerSec * secPerClus);
        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(dataStart);
        bis.skip(clusterInBytes);
        return (int) (clusterInBytes + dataStart);
    }

    /**
     * 
     * @param b byte from the beginning of the image
     */
    private static void setPointerByte(int b) throws IOException{
        bis.close();
        bis = new BufferedInputStream(new FileInputStream(new File(imgFile)));
        bis.skip(b);
    }

    /**
    Stream pointer must be set to the data entry
    */
    private static String getDataEntryName() throws IOException{
        byte[] nameBytes = getBytes(0, 11, false);
        int attr = byteToInt(getBytes(11, 1, false));
        boolean notFile = false;
        if((attr & 0x10) ==  0x10 || (attr & 0x08)== 0x08){
            notFile = true;
        }
        String name = "";
        if (nameBytes[0] == 0 || nameBytes[0] == 0xE5){
            return name + nameBytes[0];
        }
        for (int i = 0; i < nameBytes.length; i++){
            if (nameBytes[i] != 32){
                name += (char)nameBytes[i];
            }
            if(i == 7 && !notFile){
                name += ".";
            }
        }
        name = name.stripLeading();
        return name;
    }
}