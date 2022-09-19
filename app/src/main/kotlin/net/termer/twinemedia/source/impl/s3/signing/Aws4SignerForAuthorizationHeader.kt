package net.termer.twinemedia.source.impl.s3.signing

import software.amazon.awssdk.utils.BinaryUtils
import java.net.URL
import java.util.*


/**
 * This code is based on the code contained in the following zip file, adapted for Kotlin:
 * https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip
 *
 * Original comment:
 *
 * Sample AWS4 signer demonstrating how to sign requests to Amazon S3 using an
 * 'Authorization' header.
 */
class AWS4SignerForAuthorizationHeader(endpointUrl: URL, httpMethod: String, serviceName: String, regionName: String): AWS4SignerBase(endpointUrl, httpMethod, serviceName, regionName) {
	/**
	 * Computes an AWS4 signature for a request, ready for inclusion as an
	 * 'Authorization' header.
	 *
	 * @param headers
	 * The request headers; 'Host' and 'X-Amz-Date' will be added to
	 * this set.
	 * @param queryParameters
	 * Any query parameters that will be added to the endpoint. The
	 * parameters should be specified in canonical format.
	 * @param bodyHash
	 * Precomputed SHA256 hash of the request body content; this
	 * value should also be set as the header 'X-Amz-Content-SHA256'
	 * for non-streaming uploads.
	 * @param awsAccessKey
	 * The user's AWS Access Key.
	 * @param awsSecretKey
	 * The user's AWS Secret Key.
	 * @return The computed authorization string for the request. This value
	 * needs to be set as the header 'Authorization' on the subsequent
	 * HTTP request.
	 */
	fun computeSignature(headers: HashMap<String, String>, queryParameters: HashMap<String?, String?>?, bodyHash: String?, awsAccessKey: String, awsSecretKey: String): String {
		// first get the date and time for the subsequent request, and convert
		// to ISO 8601 format for use in signature generation
		val now = Date()
		val dateTimeStamp = dateTimeFormat.format(now)

		// update the headers with required 'x-amz-date' and 'host' values
		headers["x-amz-date"] = dateTimeStamp
		var hostHeader = endpointUrl.host
		val port = endpointUrl.port
		if(port > -1) {
			hostHeader += ":$port"
		}
		headers["Host"] = hostHeader

		// canonicalize the headers; we need the set of header names as well as the
		// names and values to go into the signature process
		val canonicalizedHeaderNames = getCanonicalizeHeaderNames(headers)
		val canonicalizedHeaders = getCanonicalizedHeaderString(headers)

		// if any query string parameters have been supplied, canonicalize them
		val canonicalizedQueryParameters = getCanonicalizedQueryString(queryParameters)

		// canonicalize the various components of the request
		val canonicalRequest = getCanonicalRequest(endpointUrl, httpMethod,
				canonicalizedQueryParameters, canonicalizedHeaderNames,
				canonicalizedHeaders, bodyHash!!)

		// construct the string to be signed
		val dateStamp = dateStampFormat.format(now)
		val scope = "$dateStamp/$regionName/$serviceName/$TERMINATOR"
		val stringToSign = getStringToSign(SCHEME, ALGORITHM, dateTimeStamp, scope, canonicalRequest)

		// compute the signing key
		val kSecret = (SCHEME + awsSecretKey).toByteArray()
		val kDate = sign(dateStamp, kSecret, "HmacSHA256")
		val kRegion = sign(regionName, kDate, "HmacSHA256")
		val kService = sign(serviceName, kRegion, "HmacSHA256")
		val kSigning = sign(TERMINATOR, kService, "HmacSHA256")
		val signature = sign(stringToSign, kSigning, "HmacSHA256")
		val credentialsAuthorizationHeader = "Credential=$awsAccessKey/$scope"
		val signedHeadersAuthorizationHeader = "SignedHeaders=$canonicalizedHeaderNames"
		val signatureAuthorizationHeader = "Signature=" + BinaryUtils.toHex(signature)
		return (SCHEME + "-" + ALGORITHM + " "
				+ credentialsAuthorizationHeader + ", "
				+ signedHeadersAuthorizationHeader + ", "
				+ signatureAuthorizationHeader)
	}
}
