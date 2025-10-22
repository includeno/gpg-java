# gpg-java

Java bindings for GnuPG command line tool. I built this when I wanted to unit test code I had written for an encrypted document store embedded in a large hosted application.

## Docker reproduction environment

A generic Linux container is included to make it easy to experiment with the library and reproduce the "Stream closed" exception that can occur while piping data into the `gpg` process.

1. Build the container:

   ```bash
   docker build -t gpg-java-demo .
   ```

2. Start a shell in the container:

   ```bash
   docker run --rm -it gpg-java-demo
   ```

3. Inside the container you can run the demo helper to decrypt the bundled PDF fixture:

   ```bash
   ./docker/run_demo.sh normal
   ```

   The decrypted file will be stored under `target/pdf-output`.

4. To reproduce the stream-closing failure scenario run the helper in `broken` mode:

   ```bash
   ./docker/run_demo.sh broken
   ```

   The command will intentionally abort with an `IOException: Stream closed by test harness`, matching the behaviour that occurs when the ciphertext stream is interrupted unexpectedly.
