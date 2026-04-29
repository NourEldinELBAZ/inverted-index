/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package invertedIndex;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 * @author ehab
 */
public class Test {

    public static void main(String args[]) throws IOException {
        Index5 index = new Index5();
        //|**  change it to your collection directory 
        //|**  in windows "C:\\tmp11\\rl\\collection\\"       
        String files = "C:/Users/Nour/Desktop/is322_HW_1/tmp11/rl/collection/";

        File file = new File(files);
        //|** String[] 	list()
        //|**  Returns an array of strings naming the files and directories in the directory denoted by this abstract pathname.
        String[] fileList = file.list();

        fileList = index.sort(fileList);
        index.N = fileList.length;

        for (int i = 0; i < fileList.length; i++) {
            fileList[i] = files + fileList[i];
        }
        index.buildIndex(fileList);
        index.store("index");
        index.printDictionary();

        String phraseQuery = "Reinforcement Learning";
System.out.println("Phrase query result:\n" + index.find_24_01(phraseQuery));

        // String test3 = "data  should plain greatest comif"; // data  should plain greatest comif
        // System.out.println("Boo0lean Model result = \n" + index.find_24_01(test3));

        String phrase = "";

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.println("\n===== Search Menu =====");
            System.out.println("1. Boolean Search (AND)");
            System.out.println("2. Phrase Search");
            System.out.println("0. Exit");
            System.out.println("=======================");
            System.out.print("Choose an option: ");
            
            String choice = in.readLine();
            
            switch (choice) {
                case "1":
                    System.out.print("Enter search terms: ");
                    phrase = in.readLine();
                    if (phrase == null || phrase.isEmpty()) break;
                    System.out.println("Results:\n" + index.find_24_01(phrase));
                    break;
                    
                case "2":
                    System.out.print("Enter phrase: ");
                    phrase = in.readLine();
                    if (phrase == null || phrase.isEmpty()) break;
                    System.out.println("Results:\n" + index.find_phrase(phrase));
                    break;
                    
                case "0":
                    System.out.println("Goodbye!");
                    System.exit(0);
                    break;
                    
                default:
                    System.out.println("Invalid option. Please choose 1, 2, or 0.");
            }
        }

    }
}
