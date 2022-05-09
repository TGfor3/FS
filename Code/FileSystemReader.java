package fs.code;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;


public class FileSystemReader{

    static String workingDir = "/"; 
    static byte[] FAT;  
    static int bytePerSec; 
    static int secPerClus;
    static int rsvdSecCnt;
    static int numFats;
    static int Fatsz32;

    static BufferedInputStream fatBis;
    static BufferedInputStream dataBis;
    
    public static void main(String[] args) throws FileNotFoundException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        
        fatBis = new BufferedInputStream(new FileInputStream(new File(args[0])));
        dataBis = new BufferedInputStream(new FileInputStream(new File(args[0])));
        
        Scanner cmdScanner = new Scanner(System.in);
        String task;
       
        while(true){
            System.out.println(workingDir + "]");
            task = cmdScanner.next();
            task.toLowerCase();
            switch(task){
                case "stop":
                    return;
                case "info":
                    info();
                    break;
                case "ls" :
                    ls(task);
                    break;
                case "stat":
                    stat(cmdScanner.next());
                    break;
                case "size":
                    size(cmdScanner.next());
                    break;
                case "cd":
                    cd(cmdScanner.next());
                    break;
                case "read":
                    read(cmdScanner.next(), cmdScanner.next(), cmdScanner.next());
                    break;
            }
        }



        
    }

    private static void setGlobals(){

        fatBis.mark(100000);

        fatBis.skip(11);
        bytePerSec = fatBis.read(2);
        fatBis.reset(); 
        FAT;  
        secPerClus;
        rsvdSecCnt;
        numFats;
        Fatsz32;

    }


    public static void info(){



    }
    public static void ls(String dirName){

    }
    public static void stat(String fileNameDirName){

    }
    public static void size(String fileName){

    }
    public static void cd(String dirName){

    }
    public static void read(String fileName, String offset, String numBytes){

    }
    private static boolean navigateFS(BufferedInputStream dataBis){

    }
}