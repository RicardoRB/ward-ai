import CryptoKit
import CommonCrypto

func digests(_ data: Data) {
    let first = Insecure.MD5.hash(data: data)
    let second = Insecure.SHA1.hash(data: data)
    var out = [UInt8](repeating: 0, count: Int(CC_MD5_DIGEST_LENGTH))
    CC_MD5([UInt8](data), CC_LONG(data.count), &out)
    _ = (first, second)
}
