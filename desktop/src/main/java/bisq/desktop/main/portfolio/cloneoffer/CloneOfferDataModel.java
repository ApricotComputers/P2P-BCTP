/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.portfolio.cloneoffer;


import bisq.desktop.Navigation;
import bisq.desktop.main.offer.bisq_v1.MutableOfferDataModel;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.CreateOfferService;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.payment.PaymentAccount;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import com.google.inject.Inject;

import javax.inject.Named;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

class CloneOfferDataModel extends MutableOfferDataModel {

    private final CorePersistenceProtoResolver corePersistenceProtoResolver;
    private OpenOffer sourceOpenOffer;

    @Inject
    CloneOfferDataModel(CreateOfferService createOfferService,
                        OpenOfferManager openOfferManager,
                        OfferUtil offerUtil,
                        BtcWalletService btcWalletService,
                        BsqWalletService bsqWalletService,
                        Preferences preferences,
                        User user,
                        P2PService p2PService,
                        PriceFeedService priceFeedService,
                        AccountAgeWitnessService accountAgeWitnessService,
                        FeeService feeService,
                        @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                        CorePersistenceProtoResolver corePersistenceProtoResolver,
                        TradeStatisticsManager tradeStatisticsManager,
                        Navigation navigation) {

        super(createOfferService,
                openOfferManager,
                offerUtil,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                p2PService,
                priceFeedService,
                accountAgeWitnessService,
                feeService,
                btcFormatter,
                tradeStatisticsManager,
                navigation);
        this.corePersistenceProtoResolver = corePersistenceProtoResolver;
    }

    public void reset() {
        direction = null;
        tradeCurrency = null;
        tradeCurrencyCode.set(null);
        useMarketBasedPrice.set(false);
        amount.set(null);
        minAmount.set(null);
        price.set(null);
        volume.set(null);
        minVolume.set(null);
        buyerSecurityDeposit.set(0);
        paymentAccounts.clear();
        paymentAccount = null;
        marketPriceMargin = 0;
    }

    public void applyOpenOffer(OpenOffer openOffer) {
        this.sourceOpenOffer = openOffer;

        Offer offer = openOffer.getOffer();
        direction = offer.getDirection();
        CurrencyUtil.getTradeCurrency(offer.getCurrencyCode())
                .ifPresent(c -> this.tradeCurrency = c);
        tradeCurrencyCode.set(offer.getCurrencyCode());

        PaymentAccount tmpPaymentAccount = user.getPaymentAccount(openOffer.getOffer().getMakerPaymentAccountId());
        Optional<TradeCurrency> optionalTradeCurrency = CurrencyUtil.getTradeCurrency(openOffer.getOffer().getCurrencyCode());
        if (optionalTradeCurrency.isPresent() && tmpPaymentAccount != null) {
            TradeCurrency selectedTradeCurrency = optionalTradeCurrency.get();
            this.paymentAccount = PaymentAccount.fromProto(tmpPaymentAccount.toProtoMessage(), corePersistenceProtoResolver);
            if (paymentAccount.getSingleTradeCurrency() != null)
                paymentAccount.setSingleTradeCurrency(selectedTradeCurrency);
            else
                paymentAccount.setSelectedTradeCurrency(selectedTradeCurrency);
        }

        // If the security deposit got bounded because it was below the coin amount limit, it can be bigger
        // by percentage than the restriction. We can't determine the percentage originally entered at offer
        // creation, so just use the default value as it doesn't matter anyway.
        double buyerSecurityDepositPercent = CoinUtil.getAsPercentPerBtc(offer.getBuyerSecurityDeposit(), offer.getAmount());
        if (buyerSecurityDepositPercent > Restrictions.getMaxBuyerSecurityDepositAsPercent()
                && offer.getBuyerSecurityDeposit().value == Restrictions.getMinBuyerSecurityDepositAsCoin().value)
            buyerSecurityDeposit.set(Restrictions.getDefaultBuyerSecurityDepositAsPercent());
        else
            buyerSecurityDeposit.set(buyerSecurityDepositPercent);

        allowAmountUpdate = false;
    }

    @Override
    public boolean initWithData(OfferDirection direction, TradeCurrency tradeCurrency) {
        try {
            return super.initWithData(direction, tradeCurrency);
        } catch (NullPointerException e) {
            if (e.getMessage().contains("tradeCurrency")) {
                throw new IllegalArgumentException("Offers of removed assets cannot be edited. You can only cancel it.", e);
            }
            return false;
        }
    }

    @Override
    protected Set<PaymentAccount> getUserPaymentAccounts() {
        return Objects.requireNonNull(user.getPaymentAccounts()).stream()
                .filter(account -> !account.getPaymentMethod().isBsqSwap())
                .collect(Collectors.toSet());
    }

    @Override
    protected PaymentAccount getPreselectedPaymentAccount() {
        return paymentAccount;
    }

    public void populateData() {
        Offer offer = sourceOpenOffer.getOffer();
        // Min amount need to be set before amount as if minAmount is null it would be set by amount
        setMinAmount(offer.getMinAmount());
        setAmount(offer.getAmount());
        setPrice(offer.getPrice());
        setVolume(offer.getVolume());
        setUseMarketBasedPrice(offer.isUseMarketBasedPrice());
        setTriggerPrice(sourceOpenOffer.getTriggerPrice());
        if (offer.isUseMarketBasedPrice()) {
            setMarketPriceMargin(offer.getMarketPriceMargin());
        }
    }

    public void onCloneOffer(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        Offer clonedOffer = createClonedOffer();
        openOfferManager.placeOffer(clonedOffer,
                clonedOffer.getBuyerSecurityDeposit().getValue(),
                false,
                true,
                sourceOpenOffer.getTriggerPrice(),
                transaction -> resultHandler.handleResult(),
                errorMessageHandler);
    }

    private Offer createClonedOffer() {
        Offer sourceOffer = sourceOpenOffer.getOffer();
        OfferPayload sourceOfferPayload = sourceOffer.getOfferPayload().orElseThrow();
        // We create a new offer based on our source offer and the edited fields in the UI
        Offer editedOffer = createAndGetOffer();
        OfferPayload editedOfferPayload = editedOffer.getOfferPayload().orElseThrow();
        // We clone the edited offer but use the maker tx ID from the source offer
        String sharedMakerTxId = sourceOfferPayload.getOfferFeePaymentTxId();
        OfferPayload clonedOfferPayload = new OfferPayload(editedOfferPayload.getId(),
                editedOfferPayload.getDate(),
                editedOfferPayload.getOwnerNodeAddress(),
                editedOfferPayload.getPubKeyRing(),
                editedOfferPayload.getDirection(),
                editedOfferPayload.getPrice(),
                editedOfferPayload.getMarketPriceMargin(),
                editedOfferPayload.isUseMarketBasedPrice(),
                editedOfferPayload.getAmount(),
                editedOfferPayload.getMinAmount(),
                editedOfferPayload.getBaseCurrencyCode(),
                editedOfferPayload.getCounterCurrencyCode(),
                editedOfferPayload.getArbitratorNodeAddresses(),
                editedOfferPayload.getMediatorNodeAddresses(),
                editedOfferPayload.getPaymentMethodId(),
                editedOfferPayload.getMakerPaymentAccountId(),
                sharedMakerTxId,
                editedOfferPayload.getCountryCode(),
                editedOfferPayload.getAcceptedCountryCodes(),
                editedOfferPayload.getBankId(),
                editedOfferPayload.getAcceptedBankIds(),
                editedOfferPayload.getVersionNr(),
                editedOfferPayload.getBlockHeightAtOfferCreation(),
                editedOfferPayload.getTxFee(),
                editedOfferPayload.getMakerFee(),
                editedOfferPayload.isCurrencyForMakerFeeBtc(),
                editedOfferPayload.getBuyerSecurityDeposit(),
                editedOfferPayload.getSellerSecurityDeposit(),
                editedOfferPayload.getMaxTradeLimit(),
                editedOfferPayload.getMaxTradePeriod(),
                editedOfferPayload.isUseAutoClose(),
                editedOfferPayload.isUseReOpenAfterAutoClose(),
                editedOfferPayload.getLowerClosePrice(),
                editedOfferPayload.getUpperClosePrice(),
                editedOfferPayload.isPrivateOffer(),
                editedOfferPayload.getHashOfChallenge(),
                editedOfferPayload.getExtraDataMap(),
                editedOfferPayload.getProtocolVersion());
        Offer clonedOffer = new Offer(clonedOfferPayload);
        clonedOffer.setPriceFeedService(priceFeedService);
        clonedOffer.setState(Offer.State.OFFER_FEE_PAID);
        return clonedOffer;
    }

    public boolean cannotActivateOffer() {
        Offer clonedOffer = createClonedOffer();
        return openOfferManager.cannotActivateOffer(clonedOffer);
    }
}
