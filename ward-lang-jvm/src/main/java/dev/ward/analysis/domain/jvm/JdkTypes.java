package dev.ward.analysis.domain.jvm;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The public types of the JDK packages, and the servlet API, that Ward's rules know, so that a
 * simple type name that a file imports with a wildcard ({@code import java.sql.*;}) resolves to the
 * package that holds it. Only the packages listed here are searched; any other wildcard import
 * resolves nothing.
 */
final class JdkTypes {

  private static final String CATALOG =
"""
java.io:
              BufferedInputStream BufferedOutputStream BufferedReader BufferedWriter
              ByteArrayInputStream ByteArrayOutputStream CharArrayReader CharArrayWriter
              CharConversionException ClassCache Closeable Console DataInput DataInputStream
              DataOutput DataOutputStream DefaultFileSystem DeleteOnExitHook EOFException
              Externalizable File FileCleanable FileDescriptor FileFilter FileInputStream
              FileNotFoundException FileOutputStream FilePermission FilePermissionCollection
              FileReader FileSystem FileWriter FilenameFilter FilterInputStream FilterOutputStream
              FilterReader FilterWriter Flushable IOError IOException InputStream InputStreamReader
              InterruptedIOException InvalidClassException InvalidObjectException
              LineNumberInputStream LineNumberReader NotActiveException NotSerializableException
              ObjectInput ObjectInputFilter ObjectInputStream ObjectInputValidation ObjectOutput
              ObjectOutputStream ObjectStreamClass ObjectStreamConstants ObjectStreamException
              ObjectStreamField ObjectStreamReflection OptionalDataException OutputStream
              OutputStreamWriter PipedInputStream PipedOutputStream PipedReader PipedWriter
              PrintStream PrintWriter ProxyingConsole PushbackInputStream PushbackReader
              RandomAccessFile Reader SequenceInputStream Serial SerialCallbackContext Serializable
              SerializablePermission SerializationMisdeclarationChecker StreamCorruptedException
              StreamTokenizer StringBufferInputStream StringReader StringWriter SyncFailedException
              UTFDataFormatException UncheckedIOException UnixFileSystem UnsupportedEncodingException
              WriteAbortedException Writer
java.net:
              Authenticator BindException CacheRequest CacheResponse ConnectException ContentHandler
              ContentHandlerFactory CookieHandler CookieManager CookiePolicy CookieStore
              DatagramPacket DatagramSocket DatagramSocketImpl DatagramSocketImplFactory
              DefaultInterface DelegatingSocketImpl FileNameMap HostPortrange HttpConnectSocketImpl
              HttpCookie HttpRetryException HttpURLConnection IDN InMemoryCookieStore Inet4Address
              Inet4AddressImpl Inet6Address Inet6AddressImpl InetAddress InetAddressImpl
              InetSocketAddress InterfaceAddress JarURLConnection MalformedURLException
              MulticastSocket NetMulticastSocket NetPermission NetworkInterface NoRouteToHostException
              PasswordAuthentication PortUnreachableException ProtocolException ProtocolFamily Proxy
              ProxySelector ResponseCache SecureCacheResponse ServerSocket Socket SocketAddress
              SocketException SocketImpl SocketImplFactory SocketOption SocketOptions SocketPermission
              SocketPermissionCollection SocketTimeoutException SocksConsts SocksSocketImpl
              StandardProtocolFamily StandardSocketOptions URI URISyntaxException URL URLClassLoader
              URLConnection URLDecoder URLEncoder URLPermission URLStreamHandler
              URLStreamHandlerFactory UnixDomainSocketAddress UnknownContentHandler
              UnknownHostException UnknownServiceException UrlDeserializedState
java.sql:
              Array BatchUpdateException Blob CallableStatement ClientInfoStatus Clob Connection
              ConnectionBuilder DataTruncation DatabaseMetaData Date Driver DriverAction DriverInfo
              DriverManager DriverPropertyInfo JDBCType NClob ParameterMetaData PreparedStatement
              PseudoColumnUsage Ref ResultSet ResultSetMetaData RowId RowIdLifetime
              SQLClientInfoException SQLData SQLDataException SQLException
              SQLFeatureNotSupportedException SQLInput SQLIntegrityConstraintViolationException
              SQLInvalidAuthorizationSpecException SQLNonTransientConnectionException
              SQLNonTransientException SQLOutput SQLPermission SQLRecoverableException
              SQLSyntaxErrorException SQLTimeoutException SQLTransactionRollbackException
              SQLTransientConnectionException SQLTransientException SQLType SQLWarning SQLXML
              Savepoint ShardingKey ShardingKeyBuilder Statement Struct Time Timestamp Types Wrapper
java.util:
              AbstractCollection AbstractList AbstractMap AbstractQueue AbstractSequentialList
              AbstractSet ArrayDeque ArrayList ArrayPrefixHelpers Arrays ArraysParallelSortHelpers
              Base64 BitSet Calendar CollSer Collection Collections ComparableTimSort Comparator
              Comparators ConcurrentModificationException Currency Date Deque Dictionary
              DoubleSummaryStatistics DualPivotQuicksort DuplicateFormatFlagsException
              EmptyStackException EnumMap EnumSet Enumeration EventListener EventListenerProxy
              EventObject FormatFlagsConversionMismatchException Formattable FormattableFlags
              Formatter FormatterClosedException GregorianCalendar HashMap HashSet Hashtable HexFormat
              IdentityHashMap IllegalFormatArgumentIndexException IllegalFormatCodePointException
              IllegalFormatConversionException IllegalFormatException IllegalFormatFlagsException
              IllegalFormatPrecisionException IllegalFormatWidthException IllformedLocaleException
              ImmutableCollections InputMismatchException IntSummaryStatistics
              InvalidPropertiesFormatException Iterator JapaneseImperialCalendar JumboEnumSet
              KeyValueHolder LinkedHashMap LinkedHashSet LinkedList List ListIterator
              ListResourceBundle Locale LocaleISOData LongSummaryStatistics Map
              MissingFormatArgumentException MissingFormatWidthException MissingResourceException
              NavigableMap NavigableSet NoSuchElementException Objects Observable Observer Optional
              OptionalDouble OptionalInt OptionalLong PrimitiveIterator PriorityQueue Properties
              PropertyPermission PropertyPermissionCollection PropertyResourceBundle Queue Random
              RandomAccess RegularEnumSet ResourceBundle ReverseOrderDequeView ReverseOrderListView
              ReverseOrderSortedMapView ReverseOrderSortedSetView Scanner SequencedCollection
              SequencedMap SequencedSet ServiceConfigurationError ServiceLoader Set SimpleTimeZone
              SortedMap SortedSet Spliterator Spliterators SplittableRandom Stack StringJoiner
              StringTokenizer TaskQueue TimSort TimeZone Timer TimerTask TimerThread
              TooManyListenersException TreeMap TreeSet Tripwire UUID UnknownFormatConversionException
              UnknownFormatFlagsException Vector WeakHashMap
java.util.regex:
              ASCII CharPredicates IntHashSet MatchResult Matcher Pattern PatternSyntaxException
              PrintPattern
java.nio.file:
              AccessDeniedException AccessMode AtomicMoveNotSupportedException
              ClosedDirectoryStreamException ClosedFileSystemException ClosedWatchServiceException
              CopyMoveHelper CopyOption DirectoryIteratorException DirectoryNotEmptyException
              DirectoryStream FileAlreadyExistsException FileChannelLinesSpliterator FileStore
              FileSystem FileSystemAlreadyExistsException FileSystemException FileSystemLoopException
              FileSystemNotFoundException FileSystems FileTreeIterator FileTreeWalker FileVisitOption
              FileVisitResult FileVisitor Files InvalidPathException LinkOption LinkPermission
              NoSuchFileException NotDirectoryException NotLinkException OpenOption Path PathMatcher
              Paths ProviderMismatchException ProviderNotFoundException ReadOnlyFileSystemException
              SecureDirectoryStream SimpleFileVisitor StandardCopyOption StandardOpenOption
              StandardWatchEventKinds TempFileHelper WatchEvent WatchKey WatchService Watchable
java.security:
              AccessControlContext AccessControlException AccessController AlgorithmConstraints
              AlgorithmParameterGenerator AlgorithmParameterGeneratorSpi AlgorithmParameters
              AlgorithmParametersSpi AllPermission AllPermissionCollection AsymmetricKey AuthProvider
              BasicPermission BasicPermissionCollection Certificate CodeSigner CodeSource
              CryptoPrimitive DEREncodable DigestException DigestInputStream DigestOutputStream
              DomainCombiner DomainLoadStoreParameter DrbgParameters GeneralSecurityException Guard
              GuardedObject Identity IdentityScope InvalidAlgorithmParameterException
              InvalidKeyException InvalidParameterException Key KeyException KeyFactory KeyFactorySpi
              KeyManagementException KeyPair KeyPairGenerator KeyPairGeneratorSpi KeyRep KeyStore
              KeyStoreException KeyStoreSpi MessageDigest MessageDigestSpi NoSuchAlgorithmException
              NoSuchProviderException PEMDecoder PEMEncoder PEMRecord PKCS12Attribute Permission
              PermissionCollection Permissions PermissionsEnumerator PermissionsHash Policy PolicySpi
              Principal PrivateKey PrivilegedAction PrivilegedActionException
              PrivilegedExceptionAction ProtectionDomain Provider ProviderException PublicKey
              SecureClassLoader SecureRandom SecureRandomParameters SecureRandomSpi Security
              SecurityPermission Signature SignatureException SignatureSpi SignedObject Signer
              Timestamp URIParameter UnrecoverableEntryException UnrecoverableKeyException
              UnresolvedPermission UnresolvedPermissionCollection
javax.crypto:
              AEADBadTagException BadPaddingException Cipher CipherInputStream CipherOutputStream
              CipherSpi CryptoAllPermission CryptoAllPermissionCollection CryptoPermission
              CryptoPermissionCollection CryptoPermissions CryptoPolicyParser DecapsulateException
              EncryptedPrivateKeyInfo ExemptionMechanism ExemptionMechanismException
              ExemptionMechanismSpi IllegalBlockSizeException JceSecurity JceSecurityManager KDF
              KDFParameters KDFSpi KEM KEMSpi KeyAgreement KeyAgreementSpi KeyGenerator
              KeyGeneratorSpi Mac MacSpi NoSuchPaddingException NullCipher NullCipherSpi
              PermissionsEnumerator ProviderVerifier SealedObject SecretKey SecretKeyFactory
              SecretKeyFactorySpi ShortBufferException extObjectInputStream
javax.naming:
              AuthenticationException AuthenticationNotSupportedException BinaryRefAddr Binding
              CannotProceedException CommunicationException CompositeName CompoundName
              ConfigurationException Context ContextNotEmptyException InitialContext
              InsufficientResourcesException InterruptedNamingException InvalidNameException
              LimitExceededException LinkException LinkLoopException LinkRef MalformedLinkException
              Name NameAlreadyBoundException NameClassPair NameImpl NameImplEnumerator
              NameNotFoundException NameParser NamingEnumeration NamingException
              NamingSecurityException NoInitialContextException NoPermissionException
              NotContextException OperationNotSupportedException PartialResultException RefAddr
              Reference Referenceable ReferralException ServiceUnavailableException
              SizeLimitExceededException StringRefAddr TimeLimitExceededException
javax.naming.directory:
              Attribute AttributeInUseException AttributeModificationException Attributes
              BasicAttribute BasicAttributes DirContext InitialDirContext
              InvalidAttributeIdentifierException InvalidAttributeValueException
              InvalidAttributesException InvalidSearchControlsException InvalidSearchFilterException
              ModificationItem NoSuchAttributeException SchemaViolationException SearchControls
              SearchResult
javax.naming.ldap:
              BasicControl Control ControlFactory ExtendedRequest ExtendedResponse HasControls
              InitialLdapContext LdapContext LdapName LdapReferralException ManageReferralControl
              PagedResultsControl PagedResultsResponseControl Rdn Rfc2253Parser SortControl SortKey
              SortResponseControl StartTlsRequest StartTlsResponse UnsolicitedNotification
              UnsolicitedNotificationEvent UnsolicitedNotificationListener
javax.xml.xpath:
              XPath XPathConstants XPathEvaluationResult XPathException XPathExpression
              XPathExpressionException XPathFactory XPathFactoryConfigurationException
              XPathFactoryFinder XPathFunction XPathFunctionException XPathFunctionResolver XPathNodes
              XPathVariableResolver
javax.script:
              AbstractScriptEngine Bindings Compilable CompiledScript Invocable ScriptContext
              ScriptEngine ScriptEngineFactory ScriptEngineManager ScriptException SimpleBindings
              SimpleScriptContext
java.lang.reflect:
              AccessFlag AccessibleObject AnnotatedArrayType AnnotatedElement
              AnnotatedParameterizedType AnnotatedType AnnotatedTypeVariable AnnotatedWildcardType
              Array ClassFileFormatVersion Constructor Executable Field GenericArrayType
              GenericDeclaration GenericSignatureFormatError InaccessibleObjectException
              InvocationHandler InvocationTargetException MalformedParameterizedTypeException
              MalformedParametersException Member Method Modifier Parameter ParameterizedType Proxy
              ProxyGenerator RecordComponent ReflectAccess ReflectPermission Type TypeVariable
              UndeclaredThrowableException WildcardType
javax.net.ssl:
              CertPathTrustManagerParameters DefaultSSLServerSocketFactory DefaultSSLSocketFactory
              ExtendedSSLSession HandshakeCompletedEvent HandshakeCompletedListener HostnameVerifier
              HttpsURLConnection KeyManager KeyManagerFactory KeyManagerFactorySpi
              KeyStoreBuilderParameters ManagerFactoryParameters SNIHostName SNIMatcher SNIServerName
              SSLContext SSLContextSpi SSLEngine SSLEngineResult SSLException SSLHandshakeException
              SSLKeyException SSLParameters SSLPeerUnverifiedException SSLPermission
              SSLProtocolException SSLServerSocket SSLServerSocketFactory SSLSession
              SSLSessionBindingEvent SSLSessionBindingListener SSLSessionContext SSLSocket
              SSLSocketFactory StandardConstants TrustManager TrustManagerFactory
              TrustManagerFactorySpi X509ExtendedKeyManager X509ExtendedTrustManager X509KeyManager
              X509TrustManager
java.net.http:
              HttpClient HttpConnectTimeoutException HttpHeaders HttpRequest HttpResponse
              HttpTimeoutException WebSocket WebSocketHandshakeException
javax.xml.parsers:
              DocumentBuilder DocumentBuilderFactory FactoryConfigurationError FactoryFinder
              ParserConfigurationException SAXParser SAXParserFactory
java.util.logging:
              ConsoleHandler ErrorManager FileHandler Filter Formatter Handler Level LogManager
              LogRecord Logger Logging LoggingMXBean LoggingPermission MemoryHandler SimpleFormatter
              SocketHandler StreamHandler XMLFormatter
java.util.concurrent:
              AbstractExecutorService ArrayBlockingQueue BlockingDeque BlockingQueue
              BrokenBarrierException Callable CancellationException CompletableFuture
              CompletionException CompletionService CompletionStage ConcurrentHashMap
              ConcurrentLinkedDeque ConcurrentLinkedQueue ConcurrentMap ConcurrentNavigableMap
              ConcurrentSkipListMap ConcurrentSkipListSet CopyOnWriteArrayList CopyOnWriteArraySet
              CountDownLatch CountedCompleter CyclicBarrier DelayQueue DelayScheduler Delayed
              Exchanger ExecutionException Executor ExecutorCompletionService ExecutorService
              Executors Flow ForkJoinPool ForkJoinTask ForkJoinWorkerThread Future FutureTask Helpers
              Joiners LinkedBlockingDeque LinkedBlockingQueue LinkedTransferQueue Phaser
              PriorityBlockingQueue RecursiveAction RecursiveTask RejectedExecutionException
              RejectedExecutionHandler RunnableFuture RunnableScheduledFuture ScheduledExecutorService
              ScheduledFuture ScheduledThreadPoolExecutor Semaphore StructureViolationException
              StructuredTaskScope StructuredTaskScopeImpl SubmissionPublisher SynchronousQueue
              ThreadFactory ThreadLocalRandom ThreadPerTaskExecutor ThreadPoolExecutor TimeUnit
              TimeoutException TransferQueue
javax.crypto.spec:
              ChaCha20ParameterSpec DESKeySpec DESedeKeySpec DHGenParameterSpec DHParameterSpec
              DHPrivateKeySpec DHPublicKeySpec GCMParameterSpec HKDFParameterSpec IvParameterSpec
              OAEPParameterSpec PBEKeySpec PBEParameterSpec PSource RC2ParameterSpec RC5ParameterSpec
              SecretKeySpec
java.security.spec:
              AlgorithmParameterSpec DSAGenParameterSpec DSAParameterSpec DSAPrivateKeySpec
              DSAPublicKeySpec ECField ECFieldF2m ECFieldFp ECGenParameterSpec ECParameterSpec ECPoint
              ECPrivateKeySpec ECPublicKeySpec EdDSAParameterSpec EdECPoint EdECPrivateKeySpec
              EdECPublicKeySpec EllipticCurve EncodedKeySpec InvalidKeySpecException
              InvalidParameterSpecException KeySpec MGF1ParameterSpec NamedParameterSpec
              PKCS8EncodedKeySpec PSSParameterSpec RSAKeyGenParameterSpec
              RSAMultiPrimePrivateCrtKeySpec RSAOtherPrimeInfo RSAPrivateCrtKeySpec RSAPrivateKeySpec
              RSAPublicKeySpec X509EncodedKeySpec XECPrivateKeySpec XECPublicKeySpec
javax.servlet:
  ServletRequest ServletResponse ServletContext ServletException
javax.servlet.http:
  Cookie HttpServlet HttpServletRequest HttpServletResponse HttpSession
jakarta.servlet:
  ServletRequest ServletResponse ServletContext ServletException
jakarta.servlet.http:
  Cookie HttpServlet HttpServletRequest HttpServletResponse HttpSession
""";
  private static final Map<String, Set<String>> TYPES = parse();

  private JdkTypes() {}

  /**
   * Finds the package, among the wildcard-imported ones, that holds a simple type name.
   *
   * @param simpleName the type name as written
   * @param packages the packages that the file imports with a wildcard, in source order
   * @return the qualified name, or null when no listed package holds the type
   */
  static String find(final String simpleName, final Iterable<String> packages) {
    for (final String pkg : packages) {
      if (TYPES.getOrDefault(pkg, Set.of()).contains(simpleName)) {
        return pkg + "." + simpleName;
      }
    }
    return null;
  }

  private static Map<String, Set<String>> parse() {
    final Map<String, Set<String>> types = new HashMap<>();
    Set<String> current = new HashSet<>();
    for (final String line : CATALOG.split("\n")) {
      if (line.endsWith(":")) {
        current = new HashSet<>();
        types.put(line.substring(0, line.length() - 1).strip(), current);
      } else {
        Collections.addAll(current, line.strip().split(" "));
      }
    }
    return types;
  }
}
