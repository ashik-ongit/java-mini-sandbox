# Java Mini Sandbox  

# Java Mini Sandbox  

![Java](https://img.shields.io/badge/Java-21-brightgreen?logo=java&logoColor=white) 
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20Mac-blue) 
![License](https://img.shields.io/badge/License-MIT-yellow.svg) 
![Status](https://img.shields.io/badge/Status-Active-success) 
![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)
 

---

## 📌 Overview
The **Java Mini Sandbox** is a simple GUI-based application that lets you write, compile, and run Java code in a restricted environment.  
It is designed as a **college mini-project** to demonstrate basic sandboxing techniques such as:
- Blocking dangerous imports
- Preventing file system access
- Detecting suspicious code patterns  

---

## ✨ Features
- 🖥️ Simple GUI (Swing-based)  
- ⛔ Blocks dangerous code (`System.exit`, `Runtime.exec`, etc.)  
- 🛡️ Prevents importing system-level packages like `java.io` or `java.nio.file`  
- ⚡ Real-time compilation and execution  
- 📋 Displays program output or errors inside the sandbox  

---

## 🚀 Getting Started

### Prerequisites
- [Java JDK 21](https://adoptium.net/) or higher  
- Git installed  

### Clone the repo
```bash
git clone https://github.com/ashik-ongit/java-mini-sandbox.git
cd java-mini-sandbox

```
### To Compile & Run 
```bash
javac MiniSandboxGUI.java
java MiniSandboxGUI

```
## Project Structure
``` Structure

java-mini-sandbox/
├── MiniSandboxGUI.java   # Main GUI sandbox app
├── UserProgram.java      # Example user program
├── .gitignore            # Ignore compiled files
├── README.md             # Project documentation

```

# Test Programs for Java Mini Sandbox

Copy & paste each block into the sandbox editor and click **Run Code**.

---

## 1) Normal / Safe Program
```java
public class UserProgram {
    public static void main(String[] args) {
        System.out.println("✅ Hello, world! This is a safe program.");
    }
}

```

##  2) Program with Loops & Calculation

```java
public class UserProgram {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 10; i++) {
            sum += i;
        }
        System.out.println("Sum of 1 to 10 is: " + sum);
    }
}
```


## 3) Malicious Program Type 1 — Infinite Loop / Timeout
```java
public class UserProgram {
    public static void main(String[] args) {
        while (true) {
            // infinite loop
        }
    }
}
```

## 4) Malicious Program Type 1 — System exit

```java

public class UserProgram {
    public static void main(String[] args) {
        System.exit(0);
    }
}
```

## 5) Malicious Program Type 2 — File system access

```java
import java.io.FileWriter;

public class UserProgram {
    public static void main(String[] args) throws Exception {
        FileWriter fw = new FileWriter("secret.txt");
        fw.write("Trying to write a file...");
        fw.close();
        System.out.println("File written!");
    }
}
```



## 📜 License

This project is licensed under the [MIT License](LICENSE).






