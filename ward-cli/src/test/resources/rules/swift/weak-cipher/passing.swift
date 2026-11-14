import CommonCrypto
import CryptoKit

func encrypt(_ data: Data, key: SymmetricKey) throws -> Data? {
    let algorithm = CCAlgorithm(kCCAlgorithmAES)
    _ = algorithm
    return try AES.GCM.seal(data, using: key).combined
}
