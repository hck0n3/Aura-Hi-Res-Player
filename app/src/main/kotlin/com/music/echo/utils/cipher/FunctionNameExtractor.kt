package iad1tya.echo.music.utils.cipher

import timber.log.Timber
import java.security.MessageDigest

/**
 * Extracts cipher function names from YouTube's player.js
 *
 * Handles both legacy patterns and modern Q-array obfuscation (2025+).
 * Falls back to hardcoded configs for known player.js hashes when regex fails.
 */
object FunctionNameExtractor {
    private const val TAG = "Metrolist_CipherFnExtract"

    // ==================== DATA CLASSES ====================

    data class SigFunctionInfo(
        val name: String,
        val constantArg: Int?, // The first numeric argument (e.g., 48 in JI(48, sig)) - legacy
        val constantArgs: List<Int>? = null, // All constant args e.g., JI(48, 1918, ...) -> [48, 1918]
        val preprocessFunc: String? = null, // Preprocessing function e.g., f1
        val preprocessArgs: List<Int>? = null, // Preprocess args e.g., f1(1, 6528, sig) -> [1, 6528]
        val jsExpression: String? = null,
        val isHardcoded: Boolean = false
    )

    data class NFunctionInfo(
        val name: String,
        val arrayIndex: Int?, // e.g. FUNC[0] -> index=0
        val constantArgs: List<Int>? = null, // e.g. GU(6, 6010, n) -> [6, 6010]
        val jsExpression: String? = null,
        val isHardcoded: Boolean = false
    )

    /**
     * Hardcoded player.js configuration for when regex extraction fails
     * Due to Q-array obfuscation, patterns like `.get("n")` become `Q[T^6001]`
     */
    data class HardcodedPlayerConfig(
        val sigFuncName: String,
        val sigConstantArg: Int?, // Legacy single arg
        val sigConstantArgs: List<Int>? = null, // e.g. JI(48, 1918, ...) -> [48, 1918]
        val sigPreprocessFunc: String? = null, // e.g. f1
        val sigPreprocessArgs: List<Int>? = null, // e.g. f1(1, 6528, sig) -> [1, 6528]
        val sigJsExpression: String? = null,
        val nFuncName: String,
        val nArrayIndex: Int?,
        val nConstantArgs: List<Int>?, // e.g. GU(6, 6010, n) -> [6, 6010]
        val nJsExpression: String? = null,
        val signatureTimestamp: Int
    )

    // ==================== KNOWN PLAYER CONFIGS ====================

    /**
     * Known player.js configurations indexed by hash
     *
     * Player hash 74edf1a3 (March 2026):
     * - Signature: JI(48, 1918, f1(1, 6528, sig)) -> reverse, swap(0, 57%), reverse
     * - N-transform: GU(6, 6010, n) with 87-element self-referential array
     */
    private val KNOWN_PLAYER_CONFIGS = mapOf(
        // player_ias 3891b194 (2026-08-18, rotated from c74cbcd6 within ~1h): sig=pB(20,268,JQ(74,8344,sig));
        // n=g.cY URL round-trip trick. Same verification method as c74cbcd6 below: executed the live
        // player.js in a real JS engine, confirmed output is a character-subset of the input (81 from
        // an 83-char probe) and the n transform changes its test value. sts from the file's own field.
        "3891b194" to HardcodedPlayerConfig(
            sigFuncName = "pB",
            sigConstantArg = 20,
            sigConstantArgs = listOf(20, 268),
            sigPreprocessFunc = "JQ",
            sigPreprocessArgs = listOf(74, 8344),
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.cY('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20681
        ),
        // player_ias c74cbcd6 (2026-08-18): sig=BW(27,6907,TZ(9,4260,sig)) preprocess-wrapper form;
        // n=g.kw URL round-trip trick. Both verified empirically against the live player.js by
        // executing it in a real JS engine (not guessed from regex): the sig chain produces a
        // 74-char output from a 79-char input that is a pure character-subset of the input (exactly
        // how every YT sig cipher behaves — reorder/splice, never invents characters), and the n
        // chain changes the test value as expected. sts from the same file's signatureTimestamp field.
        "c74cbcd6" to HardcodedPlayerConfig(
            sigFuncName = "BW",
            sigConstantArg = 27,
            sigConstantArgs = listOf(27, 6907),
            sigPreprocessFunc = "TZ",
            sigPreprocessArgs = listOf(9, 4260),
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.kw('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20677
        ),
        "b0d2d49a" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "EP(4,4216,sy(61,4843,INPUT))",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.eg('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20676
        ),
        "74edf1a3" to HardcodedPlayerConfig(
            sigFuncName = "JI",
            sigConstantArg = 48, // Legacy
            sigConstantArgs = listOf(48, 1918), // JI(48, 1918, processedSig)
            sigPreprocessFunc = "f1", // sig must be preprocessed through f1()
            sigPreprocessArgs = listOf(1, 6528), // f1(1, 6528, sig)
            nFuncName = "GU",
            nArrayIndex = null, // Direct function, not array access
            nConstantArgs = listOf(6, 6010), // GU(6, 6010, n) - the function requires 3 args!
            signatureTimestamp = 20522
        ),
        "f4c47414" to HardcodedPlayerConfig(
            sigFuncName = "hJ",
            sigConstantArg = 6,
            sigConstantArgs = listOf(6), // hJ(6, decodeURIComponent(h.s))
            sigPreprocessFunc = null, // No preprocessing needed
            sigPreprocessArgs = null,
            nFuncName = "", // Will be extracted via regex
            nArrayIndex = null,
            nConstantArgs = null,
            signatureTimestamp = 20543
        ),
        // May 2026: direct URLs, no client-side cipher or n-transform
        "57f5d44f" to HardcodedPlayerConfig(
            sigFuncName = "",
            sigConstantArg = null,
            sigConstantArgs = null,
            sigPreprocessFunc = null,
            sigPreprocessArgs = null,
            nFuncName = "",
            nArrayIndex = null,
            nConstantArgs = null,
            signatureTimestamp = 20591
        ),
        // player_ias 69e2a55d (2026-06-08): VM-dispatch via Jf/C6/iE. STS 20611.
        "69e2a55d" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "Jf(20,3699,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.iE('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20611
        ),
        // MD5-fallback alias for 69e2a55d
        "70d8066f" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "Jf(20,3699,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.iE('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20611
        ),
        // player_ias 9d2ef9ef (2026-06-08): VM-dispatch via v0/n7/uY. STS 20607.
        "9d2ef9ef" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "v0(35,4499,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.uY('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20607
        ),
        // MD5-fallback alias for 9d2ef9ef
        "6fb43da5" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "v0(35,4499,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.uY('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20607
        ),
        // player_ias 16ee6936 (2026-06-09): VM-dispatch via mP/Yx. STS 20613.
        // sig=mP(4,155,sig) (inner call is decodeURIComponent, pre-decoded); n=g.Yx URL-param trick.
        // Validated against the live CDN (HTTP 206).
        "16ee6936" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "mP(4,155,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.Yx('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20613
        ),
        // MD5-fallback alias for 16ee6936
        "ca366632" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "mP(4,155,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.Yx('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20613
        ),
        // player_ias ce74690f (2026-06-09): VM-dispatch via $9/cV. STS 20612.
        // sig=$9(2,6487,sig) (inner f3(4,1144,.) is decodeURIComponent, pre-decoded); n=g.cV trick.
        // Validated against the live CDN (HTTP 206).
        "ce74690f" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "\$9(2,6487,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.cV('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20612
        ),
        // MD5-fallback alias for ce74690f
        "a5669e32" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "\$9(2,6487,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.cV('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20612
        ),
        // player_ias 6b8eecd5 (2026-06-10): 16ee6936's mP/Yx generation under a new URL hash. STS 20613.
        // Validated against the live CDN (HTTP 206).
        "6b8eecd5" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "mP(4,155,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.Yx('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20613
        ),
        // MD5-fallback alias for 6b8eecd5
        "6ea478fa" to HardcodedPlayerConfig(
            sigFuncName = "_expr_sig",
            sigConstantArg = null,
            sigJsExpression = "mP(4,155,INPUT)",
            nFuncName = "_expr_n",
            nArrayIndex = null,
            nConstantArgs = null,
            nJsExpression = "(function(n){try{var u=new g.Yx('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)",
            signatureTimestamp = 20613
        )
    )

    // ==================== DETECTION PATTERNS ====================

    // Detect Q-array obfuscation: var Q="...".split("}")
    private val Q_ARRAY_PATTERN = Regex("""var\s+Q\s*=\s*"[^"]+"\s*\.\s*split\s*\(\s*"\}"\s*\)""")

    // Extract player hash from common patterns
    private val PLAYER_HASH_PATTERNS = listOf(
        Regex("""jsUrl['":\s]+[^"']*?/player/([a-f0-9]{8})/"""),
        Regex("""player_ias\.vflset/[^/]+/([a-f0-9]{8})/"""),
        Regex("""/s/player/([a-f0-9]{8})/""")
    )

    // Modern 2025+ signature deobfuscation function patterns
    private val SIG_FUNCTION_PATTERNS = listOf(
        // Pattern 1 (2025+): &&(VAR=FUNC(NUM,decodeURIComponent(VAR))
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\)"""),
        // Pattern 1a (April 2026): &&(z=hJ(6,decodeURIComponent(h.s))
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*decodeURIComponent\s*\(\s*[a-zA-Z0-9$]+\s*\.\s*[a-z]\s*\)"""),
        // Pattern 1b (2026 VM-dispatch): &&(VAR=FUNC(NUM,NUM,INPUT) — direct call with constants
        Regex("""&&\s*\(\s*[a-zA-Z0-9$]+\s*=\s*([a-zA-Z0-9$]+)\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*[a-zA-Z0-9$]+\s*\)"""),
        // Classic patterns (pre-2025, kept as fallback)
        Regex("""\b[cs]\s*&&\s*[adf]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\b[a-zA-Z0-9]+\s*&&\s*[a-zA-Z0-9]+\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
        Regex("""\bm=([a-zA-Z0-9${'$'}]{2,})\(decodeURIComponent\(h\.s\)\)"""),
        Regex("""\bc\s*&&\s*d\.set\([^,]+\s*,\s*(?:encodeURIComponent\s*\()([a-zA-Z0-9$]+)\("""),
        Regex("""\bc\s*&&\s*[a-z]\.set\([^,]+\s*,\s*encodeURIComponent\(([a-zA-Z0-9$]+)\("""),
    )

    // N-parameter (throttle) transform function patterns
    private val N_FUNCTION_PATTERNS = listOf(
        // Pattern 1: .get("n"))&&(b=FUNC[IDX](VAR)
        Regex("""\.get\("n"\)\)&&\(b=([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(([a-zA-Z0-9])\)"""),
        // Pattern 2: .get("n"))&&(FUNC=VAR[IDX](FUNC) (2025+ variant)
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*([a-zA-Z0-9$]+)(?:\[(\d+)\])?\(\1\)"""),
        // Pattern 3: .get("n");if(m){var M=n.match... (April 2026 variant)
        Regex("""\.get\("n"\);if\([a-zA-Z0-9$]+\)\s*\{[^}]*match"""),
        // Pattern 4: String.fromCharCode(110) variant (110 = 'n')
        Regex("""\(\s*([a-zA-Z0-9$]+)\s*=\s*String\.fromCharCode\(110\)"""),
        // Pattern 5: enhanced_except_ function pattern
        Regex("""([a-zA-Z0-9$]+)\s*=\s*function\([a-zA-Z0-9]\)\s*\{[^}]*?enhanced_except_"""),
        // Pattern 6 (2026 VM-dispatch): .get("n") URL param trick with g.FUNC pattern
        Regex("""\.get\("n"\)\)\s*&&\s*\(([a-zA-Z0-9$]+)\s*=\s*new\s+g\.([a-zA-Z0-9$]+)"""),
        // Pattern 7: direct assignment pattern for n-transform
        Regex("""n\s*=\s*([a-zA-Z0-9$]+)\s*\([^)]*n[^)]*\)"""),
    )

    // ==================== EXTRACTION FUNCTIONS ====================

    /**
     * Detect if player.js uses Q-array obfuscation
     */
    fun hasQArrayObfuscation(playerJs: String): Boolean {
        val hasQArray = Q_ARRAY_PATTERN.containsMatchIn(playerJs)
        Timber.tag(TAG).d("Q-array obfuscation check: hasQArray=$hasQArray")

        if (hasQArray) {
            // Try to count Q array elements for additional info
            val match = Q_ARRAY_PATTERN.find(playerJs)
            if (match != null) {
                val start = match.range.first
                val qDefEnd = playerJs.indexOf(";", start)
                if (qDefEnd > start) {
                    val qDef = playerJs.substring(start, qDefEnd)
                    val elementCount = qDef.count { it == '}' } + 1
                    Timber.tag(TAG).d("Q-array detected with ~$elementCount elements")
                }
            }
        }
        return hasQArray
    }

    /**
     * Extract player.js hash from embedded URLs or compute from content
     */
    fun extractPlayerHash(playerJs: String): String? {
        Timber.tag(TAG).d("Extracting player hash from playerJs (${playerJs.length} chars)")

        // Try to extract from embedded URLs first
        for ((index, pattern) in PLAYER_HASH_PATTERNS.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val hash = match.groupValues[1]
                Timber.tag(TAG).d("Player hash found via pattern $index: $hash")
                return hash
            }
        }

        // Fallback: compute hash from first 10KB of content
        val contentToHash = playerJs.take(10000)
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(contentToHash.toByteArray())
        val computedHash = digest.take(4).joinToString("") { "%02x".format(it) }
        Timber.tag(TAG).d("Player hash computed from content: $computedHash")
        return computedHash
    }

    /**
     * Get hardcoded config for a known player.js hash
     */
    fun getHardcodedConfig(playerHash: String): HardcodedPlayerConfig? {
        val config = KNOWN_PLAYER_CONFIGS[playerHash]
        if (config != null) {
            Timber.tag(TAG).d("Found hardcoded config for hash $playerHash:")
            Timber.tag(TAG).d("  sigFunc=${config.sigFuncName}(${config.sigConstantArg}, ...)")
            Timber.tag(TAG).d("  nFunc=${config.nFuncName}[${config.nArrayIndex}]")
            Timber.tag(TAG).d("  signatureTimestamp=${config.signatureTimestamp}")
            return config
        }

        // Self-healing second source (AUGMENT-ONLY): if YouTube rotated to a brand-new hash that isn't
        // hardcoded above, consult the remote/cached configs before giving up, so a new player.js can be
        // resolved WITHOUT an app update. Hardcoded entries always take priority (checked first, returned
        // above); this is reached ONLY on a hardcoded miss. With no remote data it returns null and we fall
        // through to the exact same warning + null as before — the hardcoded path is unchanged.
        val remote = RemotePlayerConfig.configFor(playerHash)
        if (remote != null) {
            Timber.tag(TAG).d("Found REMOTE/cached (self-healing) config for hash $playerHash:")
            Timber.tag(TAG).d("  sigFunc=${remote.sigFuncName} nFunc=${remote.nFuncName} sts=${remote.signatureTimestamp}")
            return remote
        }

        Timber.tag(TAG).w("No hardcoded config for hash: $playerHash")
        Timber.tag(TAG).w("Known hashes: ${KNOWN_PLAYER_CONFIGS.keys.joinToString()}")
        return null
    }

    /**
     * Extract signature function info from player.js
     *
     * Uses regex patterns first, falls back to hardcoded config if Q-array detected
     * @param playerJs The player.js content
     * @param knownHash Optional hash for hardcoded config lookup
     */
    fun extractSigFunctionInfo(playerJs: String, knownHash: String? = null): SigFunctionInfo? {
        Timber.tag(TAG).d("========== EXTRACTING SIG FUNCTION ==========")
        Timber.tag(TAG).d("Player.js size: ${playerJs.length} chars")

        // Hand/owner-verified config for THIS specific hash takes priority over generic regex
        // patterns. The regex patterns are loose enough (by necessity, to survive minifier churn)
        // that they can match an unrelated call site elsewhere in a ~3MB file that just happens to
        // share the same syntactic shape (e.g. "&&(x=fn(a,b,y))" appears constantly in minified JS
        // for reasons having nothing to do with the cipher) — a false positive here silently wins
        // over a verified formula and produces garbage signatures forever. A hash we've actually
        // reverse-engineered against the live player.js is trustworthy in a way a blind regex over
        // the whole file can never be, so check it first.
        val hashToUse = knownHash ?: extractPlayerHash(playerJs)
        Timber.tag(TAG).d("Checking hardcoded/remote config first for hash: $hashToUse (knownHash=$knownHash)")
        if (hashToUse != null) {
            val config = getHardcodedConfig(hashToUse)
            if (config != null) {
                if (config.sigJsExpression != null) {
                    Timber.tag(TAG).d("USING EXPRESSION-BASED SIG: ${config.sigJsExpression}")
                } else {
                    Timber.tag(TAG).d("USING HARDCODED SIG FUNCTION: ${config.sigFuncName}(${config.sigConstantArgs}, ...)")
                    Timber.tag(TAG).d("Sig preprocess: ${config.sigPreprocessFunc}(${config.sigPreprocessArgs}, sig)")
                }
                return SigFunctionInfo(
                    name = config.sigFuncName,
                    constantArg = config.sigConstantArg,
                    constantArgs = config.sigConstantArgs,
                    preprocessFunc = config.sigPreprocessFunc,
                    preprocessArgs = config.sigPreprocessArgs,
                    jsExpression = config.sigJsExpression,
                    isHardcoded = true
                )
            }
        }

        // No verified config for this hash — fall back to regex extraction against the live source.
        for ((index, pattern) in SIG_FUNCTION_PATTERNS.withIndex()) {
            Timber.tag(TAG).v("Trying sig pattern $index: ${pattern.pattern.take(60)}...")
            val match = pattern.find(playerJs)
            if (match != null) {
                // If this is Pattern 1b (2026 VM-dispatch with 2 constant args):
                if (index == 2 && match.groupValues.size >= 4) {
                    val funcName = match.groupValues[1]
                    val arg1 = match.groupValues[2]
                    val arg2 = match.groupValues[3]
                    val expr = "${funcName}(${arg1},${arg2},INPUT)"
                    Timber.tag(TAG).d("SIG FUNCTION FOUND via VM-dispatch pattern $index: expression=$expr")
                    return SigFunctionInfo("_expr_sig", null, jsExpression = expr, isHardcoded = false)
                }
                
                val name = match.groupValues[1]
                val constArg = if (match.groupValues.size > 2) match.groupValues[2].toIntOrNull() else null
                Timber.tag(TAG).d("SIG FUNCTION FOUND via pattern $index:")
                Timber.tag(TAG).d("  name=$name, constantArg=$constArg")
                Timber.tag(TAG).d("  match context: ...${playerJs.substring(maxOf(0, match.range.first - 20), minOf(playerJs.length, match.range.last + 20))}...")
                return SigFunctionInfo(name, constArg, isHardcoded = false)
            }
        }

        Timber.tag(TAG).e("========== SIG FUNCTION EXTRACTION FAILED ==========")
        Timber.tag(TAG).e("Could not find signature deobfuscation function name")
        return null
    }

    /**
     * Extract N-transform function info from player.js
     *
     * Uses regex patterns first, falls back to hardcoded config if Q-array detected
     * @param playerJs The player.js content
     * @param knownHash Optional hash for hardcoded config lookup
     */
    fun extractNFunctionInfo(playerJs: String, knownHash: String? = null): NFunctionInfo? {
        Timber.tag(TAG).d("========== EXTRACTING N-FUNCTION ==========")
        Timber.tag(TAG).d("Player.js size: ${playerJs.length} chars")

        // Hand/owner-verified config for THIS specific hash takes priority over generic regex
        // patterns — same reasoning as extractSigFunctionInfo above: a loose regex can match an
        // unrelated call site elsewhere in a ~3MB file before a verified formula ever gets a chance.
        val hashToUse = knownHash ?: extractPlayerHash(playerJs)
        Timber.tag(TAG).d("Checking hardcoded/remote config first for hash: $hashToUse (knownHash=$knownHash)")
        if (hashToUse != null) {
            val config = getHardcodedConfig(hashToUse)
            if (config != null) {
                if (config.nJsExpression != null) {
                    Timber.tag(TAG).d("USING EXPRESSION-BASED N-FUNCTION: ${config.nJsExpression.take(60)}")
                } else {
                    Timber.tag(TAG).d("USING HARDCODED N-FUNCTION: ${config.nFuncName}[${config.nArrayIndex}]")
                    Timber.tag(TAG).d("N-function constant args: ${config.nConstantArgs}")
                }
                return NFunctionInfo(config.nFuncName, config.nArrayIndex, config.nConstantArgs, config.nJsExpression, isHardcoded = true)
            }
        }

        // No verified config for this hash — fall back to regex extraction against the live source.
        for ((index, pattern) in N_FUNCTION_PATTERNS.withIndex()) {
            Timber.tag(TAG).v("Trying n-func pattern $index: ${pattern.pattern.take(60)}...")
            val match = pattern.find(playerJs)
            if (match != null) {
                when (index) {
                    0 -> {
                        val name = match.groupValues[1]
                        val arrayIdx = match.groupValues[2].toIntOrNull()
                        Timber.tag(TAG).d("N-FUNCTION FOUND via pattern $index:")
                        Timber.tag(TAG).d("  name=$name, arrayIndex=$arrayIdx")
                        return NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    1 -> {
                        val name = match.groupValues[2]
                        val arrayIdx = match.groupValues[3].toIntOrNull()
                        Timber.tag(TAG).d("N-FUNCTION FOUND via pattern $index:")
                        Timber.tag(TAG).d("  name=$name, arrayIndex=$arrayIdx")
                        return NFunctionInfo(name, arrayIdx, isHardcoded = false)
                    }
                    5 -> {
                        // Pattern 5 (index 5): .get("n"))&&(b=new g.cY
                        val nClass = match.groupValues[2]
                        val expr = "(function(n){try{var u=new g.${nClass}('https://x.googlevideo.com/videoplayback?n='+n,true);var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)"
                        Timber.tag(TAG).d("N-FUNCTION FOUND via VM-dispatch pattern $index: expression=$expr")
                        return NFunctionInfo("_expr_n", null, jsExpression = expr, isHardcoded = false)
                    }
                    else -> {
                        // Skip patterns that match but don't expose a usable function name.
                        // E.g. the `.get("n");if(...){var M=n.match...` April 2026 variant has
                        // no capturing groups and reading groupValues[1] would throw.
                        if (pattern.toPattern().matcher("").groupCount() < 1) {
                            Timber.tag(TAG).d("N-pattern $index matched but has no capture groups; skipping")
                            continue
                        }
                        val name = match.groupValues[1]
                        Timber.tag(TAG).d("N-FUNCTION FOUND via pattern $index:")
                        Timber.tag(TAG).d("  name=$name")
                        return NFunctionInfo(name, null, isHardcoded = false)
                    }
                }
            }
        }

        Timber.tag(TAG).e("========== N-FUNCTION EXTRACTION FAILED ==========")
        Timber.tag(TAG).e("Could not find n-transform function name")
        return null
    }

    /**
     * Extract signatureTimestamp from player.js
     */
    fun extractSignatureTimestamp(playerJs: String): Int? {
        Timber.tag(TAG).d("Extracting signatureTimestamp...")

        val patterns = listOf(
            Regex("""signatureTimestamp['":\s]+(\d+)"""),
            Regex("""sts['":\s]+(\d+)"""),
            Regex(""""signatureTimestamp"\s*:\s*(\d+)""")
        )

        for ((index, pattern) in patterns.withIndex()) {
            val match = pattern.find(playerJs)
            if (match != null) {
                val sts = match.groupValues[1].toIntOrNull()
                if (sts != null) {
                    Timber.tag(TAG).d("signatureTimestamp found via pattern $index: $sts")
                    return sts
                }
            }
        }

        // Fallback to hardcoded config
        val playerHash = extractPlayerHash(playerJs)
        if (playerHash != null) {
            val config = getHardcodedConfig(playerHash)
            if (config != null) {
                Timber.tag(TAG).d("Using hardcoded signatureTimestamp: ${config.signatureTimestamp}")
                return config.signatureTimestamp
            }
        }

        Timber.tag(TAG).w("Could not extract signatureTimestamp")
        return null
    }

    /**
     * Full analysis of player.js - extracts all cipher info with defensive error handling.
     * Never throws — returns a safe PlayerAnalysis with null fields on any failure.
     * @param playerJs The player.js content
     * @param knownHash Optional hash from PlayerJsFetcher (preferred over computed)
     */
    fun analyzePlayerJs(playerJs: String, knownHash: String? = null): PlayerAnalysis {
        return runCatching {
            Timber.tag(TAG).d("=== PLAYER.JS CIPHER ANALYSIS ===")

            // Use knownHash from PlayerJsFetcher if provided, otherwise extract/compute
            val playerHash = if (knownHash != null) {
                Timber.tag(TAG).d("Using known hash from PlayerJsFetcher: $knownHash")
                knownHash
            } else {
                extractPlayerHash(playerJs)
            }

            val hasQArray = hasQArrayObfuscation(playerJs)
            val sigInfo = extractSigFunctionInfo(playerJs, playerHash)
            val nFuncInfo = extractNFunctionInfo(playerJs, playerHash)
            val signatureTimestamp = extractSignatureTimestamp(playerJs)

            Timber.tag(TAG).d("=== ANALYSIS SUMMARY ===")
            Timber.tag(TAG).d("Player Hash:        ${playerHash ?: "unknown"}")
            Timber.tag(TAG).d("Q-Array Obfuscated: $hasQArray")
            Timber.tag(TAG).d("Sig Function:       ${sigInfo?.name ?: "NOT FOUND"} (hardcoded=${sigInfo?.isHardcoded})")
            Timber.tag(TAG).d("Sig Constant Arg:   ${sigInfo?.constantArg}")
            Timber.tag(TAG).d("N-Function:         ${nFuncInfo?.name ?: "NOT FOUND"} (hardcoded=${nFuncInfo?.isHardcoded})")
            Timber.tag(TAG).d("N-Array Index:      ${nFuncInfo?.arrayIndex}")
            Timber.tag(TAG).d("Signature TS:       $signatureTimestamp")

            PlayerAnalysis(
                playerHash = playerHash,
                hasQArrayObfuscation = hasQArray,
                sigInfo = sigInfo,
                nFuncInfo = nFuncInfo,
                signatureTimestamp = signatureTimestamp
            )
        }.getOrElse { e ->
            Timber.tag(TAG).e(e, "Player.js analysis failed — returning safe empty analysis")
            PlayerAnalysis(
                playerHash = knownHash ?: extractPlayerHash(playerJs),
                hasQArrayObfuscation = false,
                sigInfo = null,
                nFuncInfo = null,
                signatureTimestamp = null
            )
        }
    }

    /**
     * Safe wrapper for signature function extraction — never throws, logs at DEBUG only.
     * Returns null on any failure so caller can fall back to hardcoded/remote config.
     */
    fun extractSigFunctionInfoSafe(playerJs: String, knownHash: String? = null): SigFunctionInfo? {
        return runCatching { extractSigFunctionInfo(playerJs, knownHash) }
            .getOrElse { e ->
                Timber.tag(TAG).d("Sig extraction failed (defensive): ${e.message}")
                null
            }
    }

    /**
     * Safe wrapper for N-function extraction — never throws, logs at DEBUG only.
     * Returns null on any failure so caller can fall back to hardcoded/remote config.
     */
    fun extractNFunctionInfoSafe(playerJs: String, knownHash: String? = null): NFunctionInfo? {
        return runCatching { extractNFunctionInfo(playerJs, knownHash) }
            .getOrElse { e ->
                Timber.tag(TAG).d("N-func extraction failed (defensive): ${e.message}")
                null
            }
    }

    /**
     * Safe wrapper for signature timestamp extraction — never throws.
     */
    fun extractSignatureTimestampSafe(playerJs: String): Int? {
        return runCatching { extractSignatureTimestamp(playerJs) }
            .getOrElse { e ->
                Timber.tag(TAG).d("STS extraction failed (defensive): ${e.message}")
                null
            }
    }

    data class PlayerAnalysis(
        val playerHash: String?,
        val hasQArrayObfuscation: Boolean,
        val sigInfo: SigFunctionInfo?,
        val nFuncInfo: NFunctionInfo?,
        val signatureTimestamp: Int?
    )
}
