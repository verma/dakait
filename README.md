Dakait
=========
Download files from remote servers and effortlessly manage them.

You have large amounts of large files on a remote server (e.g. a seedbox) and you want to download and stage them nicely.  Dakait lets you do that.

You create tags (pretty looking names for local directories) and tag your remote file on a web-interface.  Dakait takes care of downloading and staging them for you.

Current Release
---
Current release version is `0.0.8`.  It will do what its supposed to do, but it has a long way to go.

[Download Now](https://github.com/verma/dakait/releases/download/0.0.8/dakait-0.0.8-standalone.jar)

How to run
---

You need a configuration file to run Dakait.  Modify and rename `config.example.json` to `config.json`. This file must reside in the working directory (path where you run this program from).

Once you have the jar file, make sure your server has a recent enough version of java.  You can start the server by running the following command

    java -jar dakait-0.0.8-standalone.jar
    
This will start a server and bind to port `3000`.  Now point your browser to `http://server-address:3000/`

What are tags?
---

Tags are an easy way of classifying a file.  

Lets say you create a tag called `Ubuntu Image` and assign it a target `images/ubuntu`.  While browsing the remote server through Dakait's web-interface, whenever you tag a file or a directory with `Ubuntu Image` Dakait will start downlaoding the tagged file/directory to your `<local-base-path>/images/ubuntu` Directory.  Note that reapplying a tag just initiates a new download, this will change in a future release when Dakait becomes smart enough to detect this.

Why?
---
I really wanted to learn clojure and really really needed a tool like this.


Configuration Options
--
Dakait understands the following options

### config-data-dir (required)
Local directory to cache running state.  Tags and tag associations are saved in this directory.

### sftp-host (required)
The remote SFTP host.  At this time Dakait support `sftp` protocol only.

### private-key (required)
The private key to use to login to the remote server.

### local-base-path (required)
Directory to save downloaded files to.  The tag target directories will be created under this directory.

### sftp-port (optional)
SSH port on remote server

### username (optional)
SSH username to use, defaults to the username of the user running the program.

### base-path (optional)
The base path to `cd` into on the remote machine, your session starts in this directory, and you cannot traverse back.

### concurrency (optional)
Number of simultaneous downloads.  Defaults to `4`.
