# twinemedia-backend
Backend for TwineMedia, a fully featured personal media management web application

# Prerequisites
You will need the following to run TwineMedia:

 - A machine running Windows, Mac, or Linux (you can use BSD if you compile Argon2-jvm for it, but it's not officially supported and won't work out of the box)
 - A Twine instance running version 1.5 or later
 - FFmpeg

# Installation
To install, download (or compile) the TwineMedia module jar and put it into Twine's `modules/` directory.

Once you have the module, run Twine with the `--twinemedia-install` option. You will be guided through
the installation process.

Once it is installed, download or compile the [frontend](https://github.com/termermc/twinemedia-frontend) and place it into any domain directory in Twine. Make sure that directory's `notFound` document is index.html, and that it has `ignore404` enabled in the domains.yml config.

Once that is all setup, start Twine and visit the frontend in your browser, sign in, and start using TwineMedia.

# API Documentation
WIP
