package com.example.ecshop.auth;

import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 住所の追加・変更・削除。どのメソッドも、ログイン中の会員 ID（userId）を必ず受け取り、取得条件に含める。
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    /** 住所が上限の件数に達しているときに投げる。 */
    public static class AddressLimitExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AddressLimitExceededException() {
            super("住所の登録数が上限に達しています");
        }
    }

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AddressService(AddressRepository addressRepository, UserRepository userRepository, Clock clock) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Address> list(long userId) {
        return addressRepository.findByUserIdOrderByIdAsc(userId);
    }

    /** 自分の住所を 1 件取得する。他人の住所や存在しない ID なら、どちらも同じ 404 にする。 */
    @Transactional(readOnly = true)
    public Address get(long userId, long addressId) {
        return findOwned(userId, addressId);
    }

    @Transactional
    public void add(long userId, AddressForm form) {
        // 会員の行をロックしてから数える。同時に追加されても上限を超えないようにするため
        userRepository.findByIdForUpdate(userId).orElseThrow();
        if (addressRepository.countByUserId(userId) >= MAX_ADDRESSES) {
            throw new AddressLimitExceededException();
        }
        addressRepository.save(Address.create(userId, form, clock.instant()));
    }

    @Transactional
    public void update(long userId, long addressId, AddressForm form) {
        findOwned(userId, addressId).update(form, clock.instant());
    }

    @Transactional
    public void delete(long userId, long addressId) {
        addressRepository.delete(findOwned(userId, addressId));
    }

    private Address findOwned(long userId, long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
