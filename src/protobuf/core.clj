(ns protobuf.core
  (:use [protobuf.schema :only [field-schema]]
        [useful.fn :only [fix]]
        [clojure.java.io :only [input-stream output-stream]]
        [clojure.string :only [lower-case]])
  (:import (protobuf.core PersistentProtocolBufferMap PersistentProtocolBufferMap$Def Extensions)
           (com.google.protobuf GeneratedMessage CodedInputStream Descriptors$Descriptor)
           (java.io InputStream OutputStream)
           (clojure.lang Reflector)))

(defn protobuf?
  "Is the given object a PersistentProtocolBufferMap?"
  [obj]
  (instance? PersistentProtocolBufferMap obj))

(defn protodef?
  "Is the given object a PersistentProtocolBufferMap$Def?"
  [obj]
  (instance? PersistentProtocolBufferMap$Def obj))

(defn protodef
  "Create a protodef from a string or protobuf class."
  ([def]
     (if (or (protodef? def) (nil? def))
       def
       (PersistentProtocolBufferMap$Def/create
        ^Descriptors.Descriptor
        (if (instance? Descriptors$Descriptor def)
          def
          (Reflector/invokeStaticMethod ^Class def "getDescriptor" (to-array nil))))))
  ([def & fields]
     (loop [^PersistentProtocolBufferMap$Def def (protodef def)
            fields fields]
       (if (empty? fields)
         def
         (recur (-> def
                    (.fieldDescriptor (first fields))
                    .getMessageType
                    protodef)
                (rest fields))))))

(defn protobuf
  "Construct a protobuf of the given type."
  ([^PersistentProtocolBufferMap$Def type]
     (PersistentProtocolBufferMap/construct type {}))
  ([^PersistentProtocolBufferMap$Def type m]
     (PersistentProtocolBufferMap/construct type m))
  ([^PersistentProtocolBufferMap$Def type k v & kvs]
     (PersistentProtocolBufferMap/construct type (apply array-map k v kvs))))

(defn protodefault
  "Return the default empty protobuf of the given type."
  [type key]
  (let [type ^PersistentProtocolBufferMap$Def (protodef type)]
    (.defaultValue type key)))

(defn protobuf-schema
  "Return the schema for the given protodef."
  [& args]
  (field-schema (apply protodef args)))

(defn protobuf-load
  "Load a protobuf of the given type from an array of bytes."
  ([^PersistentProtocolBufferMap$Def type ^bytes data]
     (when data
       (PersistentProtocolBufferMap/create type data)))
  ([^PersistentProtocolBufferMap$Def type ^bytes data ^Integer offset ^Integer length]
     (when data
       (let [^CodedInputStream in (CodedInputStream/newInstance data offset length)]
         (PersistentProtocolBufferMap/parseFrom type in)))))

(defn protobuf-load-stream
  "Load a protobuf of the given type from an InputStream."
  [^PersistentProtocolBufferMap$Def type ^InputStream stream]
  (when stream
    (let [^CodedInputStream in (CodedInputStream/newInstance stream)]
      (PersistentProtocolBufferMap/parseFrom type in))))

(defn protobuf-dump
  "Return the byte representation of the given protobuf."
  ([^PersistentProtocolBufferMap p]
     (.toByteArray p))
  ([^PersistentProtocolBufferMap$Def type m]
     (protobuf-dump (PersistentProtocolBufferMap/construct type m))))

(defn protobuf-seq
  "Lazily read a sequence of length-delimited protobufs of the specified type from the given input stream."
  [^PersistentProtocolBufferMap$Def type in]
  (lazy-seq
   (io!
    (let [^InputStream in (input-stream in)]
      (if-let [p (PersistentProtocolBufferMap/parseDelimitedFrom type in)]
        (cons p (protobuf-seq type in))
        (.close in))))))

(defn protobuf-write
  "Write the given protobufs to the given output stream, prefixing each with its length to delimit them."
  [out & ps]
  (io!
   (let [^OutputStream out (output-stream out)]
     (doseq [^PersistentProtocolBufferMap p ps]
       (.writeDelimitedTo p out))
     (.flush out))))

(defn append
  "Merge the given map into the protobuf. Equivalent to appending the byte representations."
  [^PersistentProtocolBufferMap p map]
  (.append p map))

(defn get-raw
  "Get value at key ignoring extension fields."
  [^PersistentProtocolBufferMap p key]
  (.getValAt p key false))
