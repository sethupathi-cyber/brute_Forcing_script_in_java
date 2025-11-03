# brute_Forcing_script_in_java
A simple brute forcing script built with customizable options 

## Repository contents

```

├── BruteClient.java       # CLI client that fetches CSRF token and attempts passwords from a wordlist
├── wordlist.txt           # Example wordlist (contains the correct password S3cr3t!)
└── README.md              # This file
```

---

## Prerequisites

* Java 11 or later installed.

  * Check with:

    ```bash
    java -version
    javac -version
    ```
* Terminal / command prompt.
* You must run both server and client locally (or on systems you own / are authorized to test).

---

## Quick compile & run (recommended for beginners)

1. Open a terminal and change to the project directory containing the `.java` files.

2. Compile both Java files:

```bash
javac  BruteClient.java
```



4. Run the client (in terminal):

```bash
java BruteClient --url http://localhost:8080 --user testuser --wordlist wordlist.txt --delay-ms 200
```

* Example output (truncated):

  ```
  [TRY] user='testuser' pass='password' => 401 : 401 Unauthorized: Bad credentials
  [TRY] user='testuser' pass='123456' => 401 : 401 Unauthorized: Bad credentials
  [TRY] user='testuser' pass='S3cr3t!' => 200 : 200 OK: Login successful
  [FOUND] Credentials valid! user='testuser' password='S3cr3t!'
  ```

---

## CLI options (BruteClient)

* `--url` — Base URL of target (example: `http://localhost:8080`). **Required.**
* `--user` — Username to attempt. **Required.**
* `--wordlist` — Path to a newline-separated wordlist file. **Required.**
* `--delay-ms` — Delay in milliseconds between attempts (default: `200`). Optional; helps simulate polite testing.

Usage example:

```bash
java BruteClient --url http://localhost:8080 --user testuser --wordlist wordlist.txt --delay-ms 300
```



## How the prototype works (high level)


* `BruteClient`:

  1. Fetches the login page (GET `/`) and extracts the CSRF token from the HTML.
  2. Reads the `Set-Cookie` header and returns it with subsequent POST requests.
  3. Posts username + password + CSRF token to `/login`.
  4. Prints a brief summary for each attempt; stops when a 200 / success response is observed.

This pattern demonstrates **how** a client/tester must include CSRF tokens and cookies when interacting with a protected endpoint — useful for learning and authorized security testing in a lab.

---

## Troubleshooting



  Then point the client to `--url Target`.
* **Wordlist not found**: check the path you passed to `--wordlist`. Use an absolute path if necessary.
* **Empty or malformed responses**: look at server terminal for errors; ensure the server started successfully.
* **InterruptedException / thread errors**: avoid killing the server mid-request; the client handles interruptions by re-setting the thread status.

---

## Extending the project (ideas)

* Add concurrency (thread pool) and a global rate limiter — **only** for lab/authorized testing.
* Replace the small CLI parser with `picocli` for better UX and help output.
* Add structured logging (SLF4J + logback) and JSON export of results.
* Add unit tests (JUnit) for both client parsing and the server endpoints.
* Add TLS/HTTPS support (for realism) using an embedded keystore (self-signed) — useful to teach secure transport.

---

## Ethics & legal notice (read this)

This code is provided for **learning and authorized testing only**. Do **not** use it to attack, probe, brute-force, or bypass protections for services/systems you do not own or do not have explicit written permission to test. Unauthorized use may be illegal and harmful.

If you’re using this in a classroom or on a company network, obtain written authorization and follow your organization’s rules and testing policies.

---

## Packaging (optional)

To produce runnable JARs:

1. Compile:

   ```bash
   javac MockAuthServer.java BruteClient.java
   ```
2. Create a jar for the client (example):

   ```bash
   jar cfe BruteClient.jar BruteClient *.class
   ```
3. Run:

   ```bash
   java -jar BruteClient.jar --url http://localhost:8080 --user testuser --wordlist wordlist.txt
   ```


---

## License

MIT License — feel free to use and adapt for education and authorized testing. See LICENSE file if you want to add a formal copy.

---

