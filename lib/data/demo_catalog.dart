import 'package:fbox/domain/media_item.dart';

abstract final class DemoCatalog {
  static const items = <MediaItem>[
    MediaItem(
      id: 'qing-yu-nian',
      title: '庆余年 第二季',
      note: '全36集',
      year: '2024',
      category: '连续剧',
      rating: '7.3',
      description: '范闲归京后卷入朝堂纷争，在危机与选择中继续追寻母亲留下的理想。',
      director: '孙皓',
      actors: '张若昀 / 李沁 / 陈道明',
      episodes: 36,
      color: 0xFF48627A,
    ),
    MediaItem(
      id: 'fan-hua',
      title: '繁花',
      note: '全30集',
      year: '2023',
      category: '连续剧',
      rating: '8.7',
      description: '九十年代的上海，阿宝凭借时代机遇一路成长，也在情义与生意之间经历考验。',
      director: '王家卫',
      actors: '胡歌 / 马伊琍 / 唐嫣',
      episodes: 30,
      color: 0xFF8E594F,
    ),
    MediaItem(
      id: 'hu-yao',
      title: '狐妖小红娘',
      note: '更新至28集',
      year: '2024',
      category: '连续剧',
      rating: '6.8',
      description: '东方幻想世界中的相遇与守护，一段跨越人妖两界的缘分就此展开。',
      director: '麦贯之',
      actors: '杨幂 / 龚俊 / 郭晓婷',
      episodes: 36,
      color: 0xFF40655B,
    ),
    MediaItem(
      id: 'mo-yu-yun-jian',
      title: '墨雨云间',
      note: '更新至24集',
      year: '2024',
      category: '连续剧',
      rating: '7.0',
      description: '命运被改写的女子借新身份重返京城，为自己与家人寻找真相。',
      director: '侣皓吉吉',
      actors: '吴谨言 / 王星越 / 陈鑫海',
      episodes: 40,
      color: 0xFF785165,
    ),
    MediaItem(
      id: 'ge-shou-2024',
      title: '歌手 2024',
      note: '更新至8期',
      year: '2024',
      category: '综艺',
      rating: '7.1',
      description: '实力歌手同台直播竞演，用真实的声音回应舞台。',
      director: '洪涛',
      actors: '那英 / 凡希亚 / 香缇莫',
      episodes: 12,
      color: 0xFF765842,
    ),
    MediaItem(
      id: 'hai-tian-xiong-ying',
      title: '海天雄鹰',
      note: '更新至20集',
      year: '2024',
      category: '连续剧',
      rating: '7.6',
      description: '一群优秀飞行员挑战极限，为舰载机事业写下新的篇章。',
      director: '刘家成',
      actors: '朱亚文 / 侯勇 / 李幼斌',
      episodes: 35,
      color: 0xFF3A6577,
    ),
    MediaItem(
      id: 'sha-qiu-2',
      title: '沙丘 2',
      note: '4K',
      year: '2024',
      category: '电影',
      rating: '8.2',
      description: '保罗与契妮并肩踏上复仇之路，他必须在爱情与宇宙命运之间作出选择。',
      director: '丹尼斯·维伦纽瓦',
      actors: '提莫西·查拉梅 / 赞达亚',
      episodes: 1,
      color: 0xFF8A6849,
    ),
    MediaItem(
      id: 're-la-gun-tang',
      title: '热辣滚烫',
      note: '蓝光',
      year: '2024',
      category: '电影',
      rating: '7.8',
      description: '一个普通人在拳击训练中重新找到生活方向，也找回了自己。',
      director: '贾玲',
      actors: '贾玲 / 雷佳音 / 张小斐',
      episodes: 1,
      color: 0xFF935746,
    ),
    MediaItem(
      id: 'gong-fu-xiong-mao-4',
      title: '功夫熊猫 4',
      note: '1080P',
      year: '2024',
      category: '动漫',
      rating: '7.0',
      description: '阿宝即将成为和平谷的精神领袖，同时迎来一位能力非凡的新对手。',
      director: '迈克·米切尔',
      actors: '杰克·布莱克 / 奥卡菲娜',
      episodes: 1,
      color: 0xFF56704A,
    ),
    MediaItem(
      id: 'pai-dang',
      title: '新生',
      note: '全10集',
      year: '2024',
      category: '连续剧',
      rating: '6.9',
      description: '五位陌生人被聚到同一场追思会，逐渐拼出一个人的多重面貌。',
      director: '申奥',
      actors: '井柏然 / 周依然 / 王砚辉',
      episodes: 10,
      color: 0xFF4B5665,
    ),
    MediaItem(
      id: 'cheng-feng-2024',
      title: '乘风 2024',
      note: '更新至12期',
      year: '2024',
      category: '综艺',
      rating: '7.2',
      description: '不同年龄与文化背景的女性在舞台上突破自我，共同完成全新演出。',
      director: '晏吉',
      actors: '陈昊宇 / 萨顶顶 / 戚薇',
      episodes: 12,
      color: 0xFF734A72,
    ),
    MediaItem(
      id: 'fan-ren-xiu-xian-zhuan',
      title: '凡人修仙传',
      note: '更新至110集',
      year: '2024',
      category: '动漫',
      rating: '9.0',
      description: '平凡少年韩立凭借谨慎与坚持，在修仙世界走出自己的道路。',
      director: '王裕仁',
      actors: '钱文青 / 杨天翔 / 杨默',
      episodes: 120,
      color: 0xFF3D6B6A,
    ),
  ];

  static List<MediaItem> category(String value) => value == '推荐'
      ? items
      : items.where((item) => item.category == value).toList();

  static List<MediaItem> search(String query) {
    final normalized = query.trim().toLowerCase();
    if (normalized.isEmpty) return items.take(6).toList();
    return items
        .where(
          (item) =>
              item.title.toLowerCase().contains(normalized) ||
              item.actors.toLowerCase().contains(normalized) ||
              item.category.toLowerCase().contains(normalized),
        )
        .toList();
  }

  static MediaItem byId(String id) => items.firstWhere((item) => item.id == id);
}
