import CommonCrypto

func encrypt() {
    let algorithm = CCAlgorithm(kCCAlgorithmDES)
    let options = CCOptions(kCCOptionECBMode)
    _ = (algorithm, options)
}
