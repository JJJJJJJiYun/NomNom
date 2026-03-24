import Foundation

public struct ReviewDigest: Codable, Equatable, Hashable, Sendable {
    public let bestFor: [String]
    public let pros: [String]
    public let cons: [String]

    public init(bestFor: [String], pros: [String], cons: [String]) {
        self.bestFor = bestFor
        self.pros = pros
        self.cons = cons
    }
}

public struct Restaurant: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: UUID
    public let name: String
    public let cuisine: String
    public let averagePrice: Int
    public let distanceMeters: Int
    public let rating: Double
    public let isOpenNow: Bool
    public let isFavorite: Bool
    public let neighborhood: String
    public let headline: String
    public let tags: [String]
    public let reviewDigest: ReviewDigest

    public init(
        id: UUID = UUID(),
        name: String,
        cuisine: String,
        averagePrice: Int,
        distanceMeters: Int,
        rating: Double,
        isOpenNow: Bool,
        isFavorite: Bool,
        neighborhood: String,
        headline: String,
        tags: [String],
        reviewDigest: ReviewDigest
    ) {
        self.id = id
        self.name = name
        self.cuisine = cuisine
        self.averagePrice = averagePrice
        self.distanceMeters = distanceMeters
        self.rating = rating
        self.isOpenNow = isOpenNow
        self.isFavorite = isFavorite
        self.neighborhood = neighborhood
        self.headline = headline
        self.tags = tags
        self.reviewDigest = reviewDigest
    }
}

public extension Restaurant {
    static let sampleData: [Restaurant] = [
        Restaurant(
            name: "鸟居烧肉",
            cuisine: "日式烧肉",
            averagePrice: 138,
            distanceMeters: 820,
            rating: 4.6,
            isOpenNow: true,
            isFavorite: true,
            neighborhood: "静安寺",
            headline: "主打烧肉和下酒小食，适合下班后两人轻松吃一顿。",
            tags: ["约会", "日料", "氛围好"],
            reviewDigest: ReviewDigest(
                bestFor: ["双人晚餐", "约会"],
                pros: ["牛舌口碑稳定", "环境有氛围", "服务响应快"],
                cons: ["周末排队较久", "价格略高"]
            )
        ),
        Restaurant(
            name: "汤城小厨",
            cuisine: "本帮菜",
            averagePrice: 92,
            distanceMeters: 650,
            rating: 4.3,
            isOpenNow: true,
            isFavorite: false,
            neighborhood: "静安寺",
            headline: "偏家常口味，出餐快，适合工作日晚饭或多人聚餐。",
            tags: ["家常菜", "聚餐", "出餐快"],
            reviewDigest: ReviewDigest(
                bestFor: ["工作日晚餐", "四人聚餐"],
                pros: ["菜量足", "上菜快", "性价比稳"],
                cons: ["环境偏嘈杂", "热门时段等位明显"]
            )
        ),
        Restaurant(
            name: "山葵割烹",
            cuisine: "割烹日料",
            averagePrice: 268,
            distanceMeters: 1500,
            rating: 4.8,
            isOpenNow: true,
            isFavorite: true,
            neighborhood: "南京西路",
            headline: "精致日料体验强，适合有预算的约会或庆祝场景。",
            tags: ["高端", "约会", "安静"],
            reviewDigest: ReviewDigest(
                bestFor: ["纪念日", "正式约会"],
                pros: ["出品精细", "环境安静", "服务细致"],
                cons: ["预算压力大", "位置稍远"]
            )
        ),
        Restaurant(
            name: "炭吉居酒屋",
            cuisine: "居酒屋",
            averagePrice: 118,
            distanceMeters: 430,
            rating: 4.4,
            isOpenNow: false,
            isFavorite: true,
            neighborhood: "常熟路",
            headline: "适合夜宵和小酌，串烧稳定，但开门时间偏晚。",
            tags: ["夜宵", "小酌", "串烧"],
            reviewDigest: ReviewDigest(
                bestFor: ["夜宵", "朋友小聚"],
                pros: ["串烧稳定", "酒单丰富", "气氛轻松"],
                cons: ["当前未营业", "座位偏紧凑"]
            )
        )
    ]
}
