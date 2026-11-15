import CryptoKit

func digest(_ data: Data) -> SHA256.Digest {
    return SHA256.hash(data: data)
}
