package com.printcalculator.service.payment;

import com.printcalculator.entity.Order;
import net.codecrete.qrbill.generator.Bill;
import net.codecrete.qrbill.generator.QRBill;
import org.springframework.stereotype.Service;

@Service
public class QrBillService {

    public byte[] generateQrBillSvg(Order order) {
        Bill bill = createBillFromOrder(order);
        return QRBill.generate(bill);
    }
    
    public Bill createBillFromOrder(Order order) {
        Bill bill = new Bill();
        bill.getFormat().setLanguage(net.codecrete.qrbill.generator.Language.valueOf(
                InvoiceLanguage.resolve(order.getPreferredLanguage()).code().toUpperCase(java.util.Locale.ROOT)));
        bill.getFormat().setGraphicsFormat(net.codecrete.qrbill.generator.GraphicsFormat.SVG);
        bill.getFormat().setOutputSize(net.codecrete.qrbill.generator.OutputSize.QR_BILL_ONLY);

        // Creditor (Merchant)
        bill.setAccount("CH7409000000154821581"); // TODO: Configurable IBAN
        bill.setCreditor(createAddress(
                "Joe Küng",
                "Via G. Pioda 29a",
                "6710",
                "Biasca",
                "CH"
        ));

        // Debtor (Customer)
        String debtorName;
        if ("BUSINESS".equals(order.getBillingCustomerType())) {
            debtorName = order.getBillingCompanyName();
        } else {
            debtorName = order.getBillingFirstName() + " " + order.getBillingLastName();
        }
        
        bill.setDebtor(createAddress(
                debtorName,
                order.getBillingAddressLine1(), // Assuming simple address for now. Splitting might be needed if street/house number are separate
                order.getBillingZip(),
                order.getBillingCity(),
                order.getBillingCountryCode()
        ));

        // Amount
        bill.setAmount(order.getTotalChf());
        bill.setCurrency("CHF");

        String number = order.getOrderNumber() != null && !order.getOrderNumber().isBlank()
                ? order.getOrderNumber() : order.getId().toString();
        bill.setUnstructuredMessage(InvoiceLanguage.resolve(order.getPreferredLanguage()).text("invoice")
                + " INV-" + number.toUpperCase(java.util.Locale.ROOT));

        return bill;
    }

    private net.codecrete.qrbill.generator.Address createAddress(String name, String street, String zip, String city, String country) {
        net.codecrete.qrbill.generator.Address address = new net.codecrete.qrbill.generator.Address();
        address.setName(name);
        address.setStreet(street);
        address.setPostalCode(zip);
        address.setTown(city);
        address.setCountryCode(country);
        return address;
    }
}
