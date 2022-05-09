package fs.code;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;


public class FileSystemReader{

    static String workingDir = "/"; 
    static byte[] FAT = new byte[516608];  
    static int bytePerSec; 
    
    public static void main(String[] args) throws FileNotFoundException {
        if(args.length == 0){
            throw new IllegalArgumentException("Please provide a file");
        }
        BufferedInputStream fatBis = new BufferedInputStream(new FileInputStream(new File(args[0])));
        BufferedInputStream dataBis = new BufferedInputStream(new FileInputStream(new File(args[0])));
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