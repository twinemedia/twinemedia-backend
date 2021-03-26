# twinemedia-backend
Backend for TwineMedia, a fully featured personal media management web application

# Prerequisites
You will need the following to run TwineMedia:

 - Java 8 or higher
 - A PostgreSQL instance
 - A machine running Windows, Mac, or Linux (you can use BSD if you compile Argon2-jvm for it, but it's not officially supported and won't work out of the box)
 - A Twine instance running version 2.0 or later
 - FFmpeg

# Compiling
To compile, run `./gradlew build` (Mac, Linux) or `gradlew.bat build` (Windows). The compiled module will be in `build/libs/`.
 
# Installation
To install, download (or compile) the TwineMedia module jar and put it into Twine's `modules/` directory.

Before running the installer, make sure to set `server.websocket.enable` to `true` in `twine.yml` to `true`.

Once you have the module, run Twine with the `--twinemedia-install` option. You will be guided through
the installation process.

Once it is installed, download or compile the [frontend](https://github.com/termermc/twinemedia-frontend) and place it into any domain directory in Twine. Make sure that directory's `notFound` document is index.html, and that it has `ignore404` enabled in the domains.yml config.

Once that is all setup, start Twine and visit the frontend in your browser, sign in, and start using TwineMedia.

# API Documentation
API documentation is an ongoing effort, but due to lack of time, it is not currently available.
If you want to learn how to do something with the API, you can look at what's going on with the frontend's API access using your browser's network tab.

The backend source includes documentation for API routes in every controller as well, so you can look at those for reference until a proper API document is created.