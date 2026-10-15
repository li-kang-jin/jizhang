package com.jizhang.service.support;

import cn.hutool.core.util.StrUtil;
import com.jizhang.common.constant.CommonConstants;
import com.jizhang.dao.entity.AccountEntity;
import com.jizhang.dao.entity.BaseEntity;
import com.jizhang.dao.entity.BookEntity;
import com.jizhang.dao.entity.CategoryEntity;
import com.jizhang.dao.mapper.AccountMapper;
import com.jizhang.dao.mapper.BookMapper;
import com.jizhang.dao.mapper.CategoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 新用户默认数据初始化：默认账本 + 默认账户 + 默认收支分类。
 * <p>
 * 默认分类目录后续可在管理后台维护，此处先以代码内置，保证注册即可用。
 */
@Slf4j
@Component
public class DefaultDataInitializer {

    private static final String DEFAULT_CURRENCY = "CNY";

    private record CategoryTemplate(String name, String icon, String color) {
    }

    private static final List<CategoryTemplate> EXPENSE_CATEGORIES = List.of(
            new CategoryTemplate("餐饮", "restaurant", "#FF7043"),
            new CategoryTemplate("交通", "directions_bus", "#42A5F5"),
            new CategoryTemplate("购物", "shopping_cart", "#AB47BC"),
            new CategoryTemplate("居住", "home", "#26A69A"),
            new CategoryTemplate("娱乐", "sports_esports", "#FFCA28"),
            new CategoryTemplate("医疗", "local_hospital", "#EF5350"),
            new CategoryTemplate("教育", "school", "#5C6BC0"),
            new CategoryTemplate("通讯", "phone_iphone", "#29B6F6"),
            new CategoryTemplate("人情", "card_giftcard", "#EC407A"),
            new CategoryTemplate("其他", "more_horiz", "#78909C")
    );

    private static final List<CategoryTemplate> INCOME_CATEGORIES = List.of(
            new CategoryTemplate("工资", "payments", "#66BB6A"),
            new CategoryTemplate("奖金", "emoji_events", "#FFA726"),
            new CategoryTemplate("投资", "trending_up", "#26C6DA"),
            new CategoryTemplate("红包", "redeem", "#EF5350"),
            new CategoryTemplate("其他", "more_horiz", "#78909C")
    );

    private final BookMapper bookMapper;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;

    public DefaultDataInitializer(BookMapper bookMapper,
                                  AccountMapper accountMapper,
                                  CategoryMapper categoryMapper) {
        this.bookMapper = bookMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 为新用户创建默认账本与基础数据。调用方需保证已开启事务。
     */
    public void initForNewUser(Long userId, String nickname) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        BookEntity book = new BookEntity();
        book.setName(StrUtil.isBlank(nickname) ? "我的账本" : nickname + "的账本");
        book.setIcon("book");
        book.setCurrency(DEFAULT_CURRENCY);
        book.setType(0);
        book.setOwnerId(userId);
        book.setRemark("注册时自动创建");
        book.setVersion(0);
        fillBase(book, userId, now);
        bookMapper.insert(book);

        initForNewBook(book.getId(), userId);

        log.info("用户 {} 初始化完成，默认账本 id={}", userId, book.getId());
    }

    /**
     * 为指定账本创建默认账户与收支分类（新建账本时也需要，否则记账页无可选账户）。
     * 调用方需保证已开启事务。
     */
    public void initForNewBook(Long bookId, Long userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int sort = 0;
        createAccount(bookId, "现金", 1, sort++, userId, now);
        createAccount(bookId, "支付宝", 4, sort++, userId, now);
        createAccount(bookId, "微信", 5, sort, userId, now);

        sort = 0;
        for (CategoryTemplate template : EXPENSE_CATEGORIES) {
            createCategory(bookId, template, 1, sort++, userId, now);
        }
        sort = 0;
        for (CategoryTemplate template : INCOME_CATEGORIES) {
            createCategory(bookId, template, 2, sort++, userId, now);
        }

        log.info("账本 {} 默认账户与分类创建完成", bookId);
    }

    private void createAccount(Long bookId, String name, int type, int sort, Long userId, OffsetDateTime now) {
        AccountEntity account = new AccountEntity();
        account.setBookId(bookId);
        account.setName(name);
        account.setType(type);
        account.setBalance(0L);
        account.setInitialBalance(0L);
        account.setCurrency(DEFAULT_CURRENCY);
        account.setIcon("wallet");
        account.setSort(sort);
        account.setIncludeInTotal(1);
        account.setVersion(0);
        fillBase(account, userId, now);
        accountMapper.insert(account);
    }

    private void createCategory(Long bookId, CategoryTemplate template, int type, int sort,
                                Long userId, OffsetDateTime now) {
        CategoryEntity category = new CategoryEntity();
        category.setBookId(bookId);
        category.setParentId(0L);
        category.setName(template.name());
        category.setType(type);
        category.setIcon(template.icon());
        category.setColor(template.color());
        category.setSort(sort);
        category.setVersion(0);
        fillBase(category, userId, now);
        categoryMapper.insert(category);
    }

    /** 注册流程中无登录上下文，显式补齐审计字段 */
    private void fillBase(BaseEntity entity, Long userId, OffsetDateTime now) {
        entity.setCreatedBy(userId);
        entity.setCreatedAt(now);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        entity.setDeleted(CommonConstants.NOT_DELETED);
    }
}
