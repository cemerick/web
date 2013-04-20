(ns cemerick.hashing)

(defprotocol Hashing
  (hash-of [data] [data hash-type]
    "Returns a byte[] SHA1 (by default) hash of the provided data, which can be
     a File, byte[], String (which will be converted to UTF-8 byte[]), or an InputStream.
     An alternate hash algorithm can be optionally provided."))

(extend-protocol Hashing
  (class (byte-array 0))
  (hash-of
    ([bytes] (hash-of bytes "SHA1"))
    ([bytes hash-type]
      (-> bytes java.io.ByteArrayInputStream. (hash-of hash-type))))
  String
  (hash-of
    ([s] (hash-of s "SHA1"))
    ([s hash-type] (-> s (.getBytes "UTF-8") (hash-of hash-type))))
  java.io.File
  (hash-of
    ([f] (hash-of f "SHA1"))
    ([f hash-type]
      (-> f
        java.io.FileInputStream.
        java.io.BufferedInputStream.
        (hash-of hash-type))))
  java.io.InputStream
  (hash-of
    ([is] (hash-of is "SHA1"))
    ([is hash-type]
      (let [digest (java.security.MessageDigest/getInstance hash-type)
            arr (make-array Byte/TYPE 1024)]
        (loop [len (.read is arr)]
          (when-not (== -1 len)
            (.update digest arr 0 len)
            (recur (.read is arr))))
        (.digest digest)))))

(defn ^String hex
  "Returns a hex-encoded string of the given byte[] data.
   This is intended only to hex-encode hash digests; use commons-codec
   or some other implementation if you care about hex encoding perf at all."
  [^bytes digest]
  ; just avoiding another dependency, or a local hex impl
  ; plenty good enough for hexing digest byte arrays
  (let [string (.toString (BigInteger. 1 digest) 16)]
    (if (even? (count string))
      string
      (str "0" string))))

(defn ^String base64
  "Returns a base64-encoded string of the given byte[] data.

   Optionally returns a URL-safe base64-encoded string of the given byte[] data.
   URL-safe here implies using \\- instead of \\+, \\_ instead of \\/, and
   no padding."
  ([bytes]
    (let [base64 (sun.misc.BASE64Encoder.)]
      (.encodeBuffer base64 ^bytes bytes)))
  ([bytes url-safe?]
    (let [string (base64 bytes)]
      (if-not url-safe?
        string
        (-> string
          (.replaceAll "=*\n$" "")
          (.replace \+ \-)
          (.replace \/ \_))))))

