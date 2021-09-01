package net.termer.twinemedia.source.impl.s3.signing

import software.amazon.awssdk.utils.BinaryUtils
import java.io.UnsupportedEncodingException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * This code is based on the code contained in the following zip file, adapted for Kotlin:
 * https://docs.aws.amazon.com/AmazonS3/latest/API/samples/AWSS3SigV4JavaSamples.zip
 *
 * Original comment:
 *
 * Common methods and properties for all AWS4 signer variants
 */
abstract class AWS4SignerBase(var endpointUrl: URL, var httpMethod: String, var serviceName: String, var regionName: String) {
	val dateTimeFormat: SimpleDateFormat = SimpleDateFormat(ISO8601BasicFormat).apply {
		timeZone = SimpleTimeZone(0, "UTC")
	}
	val dateStampFormat: SimpleDateFormat = SimpleDateFormat(DateStringFormat).apply {
		timeZone = SimpleTimeZone(0, "UTC")
	}

	companion object {
		/** SHA256 hash of an empty request body  */
		const val SCHEME = "AWS4"
		const val ALGORITHM = "HMAC-SHA256"
		const val TERMINATOR = "aws4_request"

		/** format strings for the date/time and date stamps required during signing  */
		const val ISO8601BasicFormat = "yyyyMMdd'T'HHmmss'Z'"
		const val DateStringFormat = "yyyyMMdd"

		private fun urlEncode(url: String?, keepPathSlash: Boolean): String {
			var encoded: String
			encoded = try {
				URLEncoder.encode(url, "UTF-8")
			} catch(e: UnsupportedEncodingException) {
				throw RuntimeException("UTF-8 encoding is not supported.", e)
			}
			if(keepPathSlash) {
				encoded = encoded.replace("%2F", "/")
			}
			return encoded
		}

		/**
		 * Returns the canonical collection of header names that will be included in
		 * the signature. For AWS4, all header names must be included in the process
		 * in sorted canonicalized order.
		 */
		fun getCanonicalizeHeaderNames(headers: Map<String, String?>): String {
			val sortedHeaders: MutableList<String> = ArrayList()
			sortedHeaders.addAll(headers.keys)
			Collections.sort(sortedHeaders, java.lang.String.CASE_INSENSITIVE_ORDER)
			val buffer = StringBuilder()
			for(header in sortedHeaders) {
				if(buffer.isNotEmpty()) buffer.append(";")
				buffer.append(header.toLowerCase())
			}
			return buffer.toString()
		}

		/**
		 * Computes the canonical headers with values for the request. For AWS4, all
		 * headers must be included in the signing process.
		 */
		fun getCanonicalizedHeaderString(headers: Map<String, String>?): String {
			if(headers == null || headers.isEmpty()) {
				return ""
			}

			// step1: sort the headers by case-insensitive order
			val sortedHeaders: MutableList<String> = ArrayList()
			sortedHeaders.addAll(headers.keys)
			Collections.sort(sortedHeaders, java.lang.String.CASE_INSENSITIVE_ORDER)

			// step2: form the canonical header:value entries in sorted order.
			// Multiple white spaces in the values should be compressed to a single
			// space.
			val buffer = StringBuilder()
			for(key in sortedHeaders) {
				buffer.append(key.toLowerCase().replace("\\s+".toRegex(), " ") + ":" + headers[key]!!.replace("\\s+".toRegex(), " "))
				buffer.append("\n")
			}
			return buffer.toString()
		}

		/**
		 * Returns the canonical request string to go into the signer process; this
		 * consists of several canonical sub-parts.
		 * @return
		 */
		fun getCanonicalRequest(endpoint: URL?, httpMethod: String, queryParameters: String, canonicalizedHeaderNames: String, canonicalizedHeaders: String, bodyHash: String): String {
			return "$httpMethod\n${getCanonicalizedResourcePath(endpoint)}\n$queryParameters\n$canonicalizedHeaders\n$canonicalizedHeaderNames\n$bodyHash"
		}

		/**
		 * Returns the canonicalized resource path for the service endpoint.
		 */
		private fun getCanonicalizedResourcePath(endpoint: URL?): String {
			if(endpoint == null) {
				return "/"
			}
			val path = endpoint.path
			if(path == null || path.isEmpty()) {
				return "/"
			}
			val encodedPath: String = urlEncode(path, true)
			return if(encodedPath.startsWith("/")) {
				encodedPath
			} else {
				"/$encodedPath"
			}
		}

		/**
		 * Examines the specified query string parameters and returns a
		 * canonicalized form.
		 *
		 *
		 * The canonicalized query string is formed by first sorting all the query
		 * string parameters, then URI encoding both the key and value and then
		 * joining them, in order, separating key value pairs with an '&'.
		 *
		 * @param parameters
		 * The query string parameters to be canonicalized.
		 *
		 * @return A canonicalized form for the specified query string parameters.
		 */
		fun getCanonicalizedQueryString(parameters: Map<String?, String?>?): String {
			if(parameters == null || parameters.isEmpty()) {
				return ""
			}
			val sorted: SortedMap<String?, String?> = TreeMap()
			var pairs = parameters.entries.iterator()
			while(pairs.hasNext()) {
				val (key, value) = pairs.next()
				sorted[urlEncode(key, false)] = urlEncode(value, false)
			}
			val builder = StringBuilder()
			pairs = sorted.entries.iterator()
			while(pairs.hasNext()) {
				val (key, value) = pairs.next()
				builder.append(key)
				builder.append("=")
				builder.append(value)
				if(pairs.hasNext()) {
					builder.append("&")
				}
			}
			return builder.toString()
		}

		fun getStringToSign(scheme: String, algorithm: String, dateTime: String, scope: String, canonicalRequest: String): String {
			return "$scheme-$algorithm\n$dateTime\n$scope\n${BinaryUtils.toHex(hash(canonicalRequest))}"
		}

		/**
		 * Hashes the string contents (assumed to be UTF-8) using the SHA-256
		 * algorithm.
		 */
		fun hash(text: String): ByteArray {
			return try {
				val md = MessageDigest.getInstance("SHA-256")
				md.update(text.toByteArray(charset("UTF-8")))
				md.digest()
			} catch(e: Exception) {
				throw RuntimeException("Unable to compute hash while signing request: " + e.message, e)
			}
		}

		/**
		 * Hashes the byte array using the SHA-256 algorithm.
		 */
		fun hash(data: ByteArray?): ByteArray {
			return try {
				val md = MessageDigest.getInstance("SHA-256")
				md.update(data)
				md.digest()
			} catch(e: Exception) {
				throw RuntimeException("Unable to compute hash while signing request: " + e.message, e)
			}
		}

		fun sign(stringData: String, key: ByteArray?, algorithm: String?): ByteArray {
			return try {
				val data = stringData.toByteArray(charset("UTF-8"))
				val mac = Mac.getInstance(algorithm)
				mac.init(SecretKeySpec(key, algorithm))
				mac.doFinal(data)
			} catch(e: Exception) {
				throw RuntimeException("Unable to calculate a request signature: " + e.message, e)
			}
		}
	}
}
