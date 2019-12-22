#!/bin/bash
echo "Creating keystore for JWT signing..."
keytool -genseckey -keystore jwt.jceks -storetype jceks -keyalg HMacSHA256 -keysize 2048 -alias HS256