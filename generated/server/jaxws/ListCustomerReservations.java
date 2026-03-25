
package server.jaxws;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "listCustomerReservations", namespace = "http://server/")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "listCustomerReservations", namespace = "http://server/")
public class ListCustomerReservations {

    @XmlElement(name = "customerID", namespace = "")
    private String customerID;

    /**
     * 
     * @return
     *     returns String
     */
    public String getCustomerID() {
        return this.customerID;
    }

    /**
     * 
     * @param customerID
     *     the value for the customerID property
     */
    public void setCustomerID(String customerID) {
        this.customerID = customerID;
    }

}
